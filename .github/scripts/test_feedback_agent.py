import datetime as dt
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / ".agents" / "skills" / "mcp-feedback-research" / "scripts" / "feedback_agent.py"
SPEC = importlib.util.spec_from_file_location("feedback_agent", MODULE_PATH)
feedback_agent = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = feedback_agent
SPEC.loader.exec_module(feedback_agent)


def completed(args, returncode=0, stdout="", stderr=""):
    return subprocess.CompletedProcess(args=args, returncode=returncode, stdout=stdout, stderr=stderr)


def source_item(**overrides):
    record = {
        "id": "src-1",
        "source": "github",
        "source_url": "https://github.com/acme/repo/discussions/1",
        "thread_url": "https://github.com/acme/repo/discussions/1",
        "source_repository": "acme/repo",
        "source_type": "discussion",
        "source_node_id": "D_1",
        "discussion_node_id": "D_1",
        "author": "builder-one",
        "published_at": "2026-10-04T08:00:00Z",
        "updated_at": "2026-10-04T08:00:00Z",
        "title": "MCP security and read-only tools",
        "text": "I am using MCP in an agent and cannot prove that a read-only server has no write path.",
        "parent_id": "",
    }
    record.update(overrides)
    return record


class TemporaryStoreTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.store = feedback_agent.StateStore(self.root / "state")
        self.config = {
            "github": {"enabled": True, "repositories": []},
            "hackerNews": {"enabled": False},
            "reddit": {"enabled": False},
            "thresholds": {
                "collectRelevance": 0.60,
                "engagementRelevance": 0.75,
                "roadmapIndependentAuthors": 3,
                "roadmapIndependentThreads": 2,
            },
        }

    def tearDown(self):
        self.temporary.cleanup()


class CollectionTests(TemporaryStoreTest):
    def test_duplicate_source_item_is_not_inserted_twice(self):
        item = source_item()
        self.assertEqual(1, self.store.upsert("inbox.jsonl", [item]))
        self.assertEqual(0, self.store.upsert("inbox.jsonl", [item]))
        self.assertEqual(1, len(self.store.records("inbox.jsonl")))

    def test_malformed_github_result_does_not_break_collection(self):
        calls = 0

        def runner(args):
            nonlocal calls
            calls += 1
            if args[:3] == ["gh", "auth", "status"]:
                return completed(args)
            return completed(args, stdout="not-json")

        adapter = feedback_agent.GitHubDiscussionsAdapter(
            {"enabled": True, "repositories": [{"owner": "acme", "repo": "repo", "discussions": True}]}, runner
        )
        items, errors = adapter.collect(dt.datetime(2026, 10, 3, tzinfo=dt.timezone.utc))
        self.assertEqual([], items)
        self.assertTrue(any("malformed" in error for error in errors))
        self.assertGreaterEqual(calls, 2)

    def test_old_github_discussion_is_filtered_and_urls_are_canonical(self):
        recent = {
            "id": "D_recent", "title": "MCP registry", "body": "MCP discovery problem",
            "url": "https://github.com/acme/repo/discussions/9", "createdAt": "2026-10-03T00:00:00Z",
            "updatedAt": "2026-10-04T00:00:00Z", "author": {"login": "dev"}, "comments": {"nodes": []},
        }
        old = dict(
            recent,
            id="D_old",
            url="https://github.com/acme/repo/discussions/8",
            createdAt="2026-09-01T00:00:00Z",
            updatedAt="2026-10-04T00:00:00Z",
        )
        mapped_recent = feedback_agent.GitHubDiscussionsAdapter._map_discussion(
            "acme", "repo", recent, dt.datetime(2026, 10, 3, tzinfo=dt.timezone.utc)
        )
        mapped_old = feedback_agent.GitHubDiscussionsAdapter._map_discussion(
            "acme", "repo", old, dt.datetime(2026, 10, 3, tzinfo=dt.timezone.utc)
        )
        self.assertEqual("https://github.com/acme/repo/discussions/9", mapped_recent[0].source_url)
        self.assertEqual([], mapped_old)

    def test_hn_adapter_produces_canonical_item_url(self):
        item = {"id": 123, "by": "dev", "time": 1791100800, "title": "Ask HN: MCP discovery", "kids": []}
        mapped = feedback_agent.HackerNewsAdapter._map_item(item, item, "hn_story")
        self.assertEqual("https://news.ycombinator.com/item?id=123", mapped.source_url)
        self.assertEqual(mapped.source_url, mapped.thread_url)

    def test_collection_links_public_responses_to_recorded_engagement(self):
        self.store.upsert("engagements.jsonl", [{
            "id": "engagement-1", "commentId": "DC_123", "status": "POSTED"
        }])
        item = feedback_agent.SourceItem(**source_item(
            id="src-response", source_node_id="DC_456", source_type="comment", parent_id="DC_123"
        ))

        class Adapter:
            name = "github"

            def collect(self, since):
                return [item], []

        original_github = feedback_agent.GitHubDiscussionsAdapter
        original_hn = feedback_agent.HackerNewsAdapter
        original_reddit = feedback_agent.RedditAdapter
        try:
            class GitHubStub:
                name = "github"

                def __init__(self, config, runner=None):
                    pass

                def collect(self, since):
                    return [item], []

                def auth(self):
                    return True, ""

            class EmptyStub:
                name = "hackernews"

                def __init__(self, config, opener=None):
                    pass

                def collect(self, since):
                    return [], []

            class RedditStub(EmptyStub):
                name = "reddit"

                def status(self):
                    return "DISABLED_NOT_CONFIGURED"

            feedback_agent.GitHubDiscussionsAdapter = GitHubStub
            feedback_agent.HackerNewsAdapter = EmptyStub
            feedback_agent.RedditAdapter = RedditStub
            feedback_agent.collect_command(
                self.config, self.store, dt.datetime(2026, 10, 3, tzinfo=dt.timezone.utc)
            )
        finally:
            feedback_agent.GitHubDiscussionsAdapter = original_github
            feedback_agent.HackerNewsAdapter = original_hn
            feedback_agent.RedditAdapter = original_reddit
        engagement = self.store.records("engagements.jsonl")[0]
        self.assertEqual("RESPONSE_RECEIVED", engagement["status"])
        self.assertEqual(1, engagement["responseCount"])


class RelevanceAndEvidenceTests(TemporaryStoreTest):
    def test_relevant_security_is_high_generic_ai_and_spam_are_low(self):
        relevant = source_item()
        generic = source_item(title="Thoughts on AI", text="AI will change many industries")
        spam = source_item(title="MCP directory", text="Limited time: buy now using my affiliate link")
        self.assertGreaterEqual(feedback_agent.relevance_score(relevant), 0.90)
        self.assertLess(feedback_agent.relevance_score(generic), 0.60)
        self.assertLess(feedback_agent.relevance_score(spam), 0.60)

    def test_repeated_content_is_deduplicated(self):
        first = feedback_agent.evidence_from_item(source_item())
        second = feedback_agent.evidence_from_item(source_item(
            id="src-2", source_node_id="C_2", source_url="https://github.com/acme/repo/discussions/1#discussioncomment-2"
        ))
        self.assertTrue(feedback_agent.is_duplicate(second, [first]))

    def test_same_problem_clusters_and_one_author_does_not_inflate(self):
        reference = dt.datetime(2026, 10, 4, 12, tzinfo=dt.timezone.utc)
        records = []
        for index, (author, thread) in enumerate((
            ("same-author", "https://example/one"),
            ("same-author", "https://example/two"),
            ("other-author", "https://example/two"),
        )):
            item = source_item(
                id=f"src-{index}", source_node_id=f"N_{index}", source_url=f"{thread}#comment-{index}",
                thread_url=thread, author=author, text="A read-only MCP still exposes an arbitrary SQL write path.",
            )
            records.append(feedback_agent.evidence_from_item(item, reference))
        clusters = feedback_agent.build_clusters(records, self.config["thresholds"], reference)
        cluster = clusters[0]
        self.assertEqual("forbidden-capability-verification", cluster["problemKey"])
        self.assertEqual(2, cluster["uniqueAuthors"])
        self.assertEqual(2, cluster["independentEvidenceCount"])
        self.assertEqual("insufficient-evidence", cluster["roadmapStatus"])

    def test_independent_authors_and_threads_make_roadmap_candidate(self):
        records = []
        for index in range(3):
            records.append(feedback_agent.evidence_from_item(source_item(
                id=f"src-{index}", source_node_id=f"N_{index}", author=f"author-{index}",
                source_url=f"https://example/{index}", thread_url=f"https://example/{index % 2}",
            )))
        cluster = feedback_agent.build_clusters(records, self.config["thresholds"])[0]
        self.assertEqual("candidate", cluster["roadmapStatus"])

    def test_low_potential_user_is_not_an_engagement_opportunity(self):
        low = feedback_agent.evidence_from_item(source_item())
        low["relevanceScore"] = 0.95
        low["potentialUserScore"] = 43
        self.store.upsert("evidence.jsonl", [low])
        self.assertEqual([], feedback_agent.eligible_opportunities(self.config, self.store))


class EngagementTests(TemporaryStoreTest):
    def setUp(self):
        super().setUp()
        evidence = feedback_agent.evidence_from_item(source_item())
        self.store.upsert("evidence.jsonl", [evidence])
        self.proposal = feedback_agent.create_proposal(self.store, evidence["id"], account="operator")

    def test_reply_cannot_publish_without_explicit_approval(self):
        class NeverPublisher:
            def publish(self, proposal):
                raise AssertionError("publisher must not be called")

        with self.assertRaisesRegex(ValueError, "Explicit approval"):
            feedback_agent.publish_proposal(self.store, self.proposal["id"], NeverPublisher())

    def test_modified_final_copy_invalidates_approval(self):
        feedback_agent.approve_proposal(self.store, self.proposal["id"], self.proposal["hash"])
        proposals = self.store.records("proposals.jsonl")
        proposals[0]["body"] += " changed"
        self.store.replace_records("proposals.jsonl", proposals)
        with self.assertRaisesRegex(ValueError, "changed"):
            feedback_agent.publish_proposal(self.store, self.proposal["id"], object())

    def test_wrong_target_invalidates_approval(self):
        feedback_agent.approve_proposal(self.store, self.proposal["id"], self.proposal["hash"])
        proposals = self.store.records("proposals.jsonl")
        proposals[0]["targetUrl"] = "https://github.com/acme/other/discussions/2"
        self.store.replace_records("proposals.jsonl", proposals)
        with self.assertRaisesRegex(ValueError, "changed"):
            feedback_agent.publish_proposal(self.store, self.proposal["id"], object())

    def test_failed_github_auth_leaves_draft_and_reports_unavailable(self):
        feedback_agent.approve_proposal(self.store, self.proposal["id"], self.proposal["hash"])

        def runner(args):
            return completed(args, returncode=1, stderr="not logged in")

        with self.assertRaisesRegex(RuntimeError, "PUBLISHING_UNAVAILABLE"):
            feedback_agent.publish_proposal(
                self.store, self.proposal["id"], feedback_agent.GitHubPublisher(runner)
            )
        self.assertEqual("DRAFT", self.store.records("proposals.jsonl")[0]["status"])
        self.assertEqual([], self.store.records("engagements.jsonl"))

    def test_successful_publish_records_returned_identifier_and_url(self):
        feedback_agent.approve_proposal(self.store, self.proposal["id"], self.proposal["hash"])

        class Publisher:
            def publish(self, proposal):
                return {"id": "DC_123", "url": "https://github.com/acme/repo/discussions/1#discussioncomment-5"}

        result = feedback_agent.publish_proposal(self.store, self.proposal["id"], Publisher())
        self.assertEqual("DC_123", result["commentId"])
        self.assertEqual(result, self.store.records("engagements.jsonl")[0])
        self.assertIsNotNone(self.store.records("approvals.jsonl")[0]["consumedAt"])

    def test_failed_mutation_consumes_approval_and_prevents_implicit_retry(self):
        feedback_agent.approve_proposal(self.store, self.proposal["id"], self.proposal["hash"])

        class FailedPublisher:
            def publish(self, proposal):
                raise RuntimeError("network result unknown")

        with self.assertRaisesRegex(RuntimeError, "unknown"):
            feedback_agent.publish_proposal(self.store, self.proposal["id"], FailedPublisher())
        attempt = self.store.records("engagements.jsonl")[0]
        self.assertEqual("PUBLISH_FAILED_OR_UNCERTAIN", attempt["status"])
        self.assertIsNotNone(self.store.records("approvals.jsonl")[0]["consumedAt"])
        with self.assertRaisesRegex(ValueError, "publication attempt"):
            feedback_agent.approve_proposal(self.store, self.proposal["id"], self.proposal["hash"])


class SafetyTests(TemporaryStoreTest):
    def test_credentials_are_redacted_from_generated_report(self):
        evidence = feedback_agent.evidence_from_item(source_item(
            text="MCP auth is unclear; api_key=super-secret-token and Authorization: Bearer abc123"
        ))
        self.store.upsert("evidence.jsonl", [evidence])
        feedback_agent.write_json(
            self.store.path("clusters.json"),
            feedback_agent.build_clusters([evidence], self.config["thresholds"]),
        )
        report, _ = feedback_agent.report_command(self.config, self.store)
        rendered = report.read_text(encoding="utf-8")
        self.assertNotIn("super-secret-token", rendered)
        self.assertNotIn("abc123", rendered)

    def test_raw_state_directory_is_ignored_by_git(self):
        ignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
        self.assertIn("/.local/feedback/", ignore)

    def test_hn_and_reddit_have_no_publish_action(self):
        self.assertFalse(hasattr(feedback_agent.HackerNewsAdapter, "publish"))
        self.assertFalse(hasattr(feedback_agent.RedditAdapter, "publish"))
        self.assertEqual("DISABLED_NOT_CONFIGURED", feedback_agent.RedditAdapter({"enabled": False}).status())
        parser_help = feedback_agent.build_parser().format_help()
        self.assertNotIn("reddit-publish", parser_help)
        self.assertNotIn("hn-publish", parser_help)


if __name__ == "__main__":
    unittest.main()
