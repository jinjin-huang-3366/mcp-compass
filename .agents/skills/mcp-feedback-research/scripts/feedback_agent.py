#!/usr/bin/env python3
"""Local, deterministic feedback intelligence for MCP Compass."""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
import html
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from collections import Counter, deque
from pathlib import Path
from typing import Any, Callable, Iterable, Protocol


UTC = dt.timezone.utc
REPO_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_CONFIG = REPO_ROOT / "feedback" / "sources.json"
DEFAULT_STATE = REPO_ROOT / ".local" / "feedback"
MAX_TEXT = 3000
APPROVAL_MINUTES = 30
REDDIT_STATES = {"ENABLED", "DISABLED_NO_CREDENTIALS", "DISABLED_NOT_CONFIGURED"}

SECRET_PATTERN = re.compile(
    r"(?i)(authorization\s*:\s*(?:bearer|token)\s+|(?:api[_-]?key|access[_-]?token|password|cookie)\s*[=:]\s*)[^\s,;]+"
)
URL_PATTERN = re.compile(r"https?://\S+")
HTML_PATTERN = re.compile(r"<[^>]+>")
WORD_PATTERN = re.compile(r"[a-z0-9]+")

PROMO_MARKERS = (
    "limited time",
    "buy now",
    "sign up with my",
    "affiliate",
    "sponsored",
    "use my referral",
)

CONCEPTS: dict[str, tuple[str, ...]] = {
    "mcp": ("mcp", "model context protocol", "tools/list"),
    "discovery": ("mcp discovery", "server selection", "mcp directory", "mcp marketplace", "mcp registry", "agent tool selection"),
    "security": ("mcp security", "mcp permission", "read-only mcp", "forbidden capability", "write path", "arbitrary sql", "delete"),
    "validation": ("mcp validation", "mcp verification", "runtime testing", "runtime probing", "tool schema", "capability verification"),
    "reliability": ("dead mcp", "broken mcp", "unreachable mcp", "dead endpoint", "compatibility"),
    "authentication": ("mcp authentication", "mcp auth", "oauth", "credentials"),
    "generation": ("mcp generation", "openapi to mcp", "openapi -> mcp", "generate mcp"),
    "ranking": ("mcp ranking", "compare mcp", "choosing mcp", "selecting mcp"),
    "builder": ("building an agent", "build an agent", "using mcp", "mcp server author", "maintain an mcp", "integrating mcp"),
}

PROBLEM_RULES: tuple[dict[str, Any], ...] = (
    {
        "key": "forbidden-capability-verification",
        "title": "Forbidden capabilities cannot be verified from declarations alone",
        "statement": "Developers cannot prove that prohibited capabilities are unreachable from Registry metadata or names alone.",
        "area": "capability-verification",
        "terms": ("forbidden capability", "read-only", "write path", "arbitrary sql", "delete", "permission", "self-described", "runtime probing"),
        "features": ("tool schema inspection", "schema risk inference", "runtime probing", "evidence levels"),
    },
    {
        "key": "server-discovery-selection",
        "title": "MCP server discovery and comparison are too manual",
        "statement": "Developers struggle to discover and compare MCP servers against concrete agent requirements.",
        "area": "search-ranking",
        "terms": ("mcp discovery", "server selection", "mcp directory", "marketplace", "mcp ranking", "compare mcp", "choosing mcp"),
        "features": ("requirement parsing", "capability comparison", "ranking explanations"),
    },
    {
        "key": "server-trust-security",
        "title": "MCP server trust evidence is insufficient",
        "statement": "Developers lack evidence to evaluate MCP server trust, permissions, and security posture.",
        "area": "trust-security",
        "terms": ("mcp security", "server trust", "trusted mcp", "malicious mcp", "supply chain", "permission"),
        "features": ("provenance", "risk classification", "security evidence labels"),
    },
    {
        "key": "dead-unreachable-servers",
        "title": "Listed MCP servers are dead or unreachable",
        "statement": "Developers encounter MCP servers that are listed but cannot be installed or reached.",
        "area": "registry-quality",
        "terms": ("dead mcp", "broken mcp", "unreachable", "dead endpoint", "404", "not maintained"),
        "features": ("reachability checks", "freshness evidence", "installability status"),
    },
    {
        "key": "authentication-requirements-unclear",
        "title": "MCP authentication requirements are unclear",
        "statement": "Developers cannot determine required authentication and credential setup before choosing a server.",
        "area": "authentication",
        "terms": ("mcp auth", "authentication", "oauth", "api key", "credentials unclear"),
        "features": ("authentication metadata", "setup evidence", "credential requirement filters"),
    },
    {
        "key": "tool-schema-compatibility",
        "title": "MCP tool schemas and compatibility are hard to assess",
        "statement": "Developers cannot reliably compare tool schemas or client compatibility before integration.",
        "area": "tool-metadata",
        "terms": ("tools/list", "tool schema", "schema mismatch", "compatibility", "client support"),
        "features": ("tool schema inspection", "compatibility metadata", "normalized capabilities"),
    },
    {
        "key": "openapi-mcp-generation",
        "title": "Turning existing APIs into MCP servers is costly",
        "statement": "Developers need a reviewable path from an existing API contract to an MCP server.",
        "area": "generation",
        "terms": ("openapi to mcp", "openapi -> mcp", "mcp generation", "generate mcp"),
        "features": ("contract-first generation", "endpoint selection", "generated project validation"),
    },
    {
        "key": "mcp-runtime-validation",
        "title": "Declared MCP behavior lacks runtime evidence",
        "statement": "Developers need bounded runtime evidence that an MCP server behaves as declared.",
        "area": "validation",
        "terms": ("mcp validation", "runtime testing", "runtime evidence", "mcp verification", "runtime probing"),
        "features": ("isolated runtime validation", "protocol checks", "observed evidence labels"),
    },
)

URGENT_MARKERS = (
    "data loss",
    "credential leak",
    "remote code execution",
    "security vulnerability",
    "false safety",
    "compliance violation",
    "legal violation",
    "deleted my",
)


def now_utc() -> dt.datetime:
    return dt.datetime.now(UTC)


def iso(value: dt.datetime) -> str:
    return value.astimezone(UTC).isoformat().replace("+00:00", "Z")


def parse_time(value: str | None) -> dt.datetime | None:
    if not value:
        return None
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
        return parsed.replace(tzinfo=parsed.tzinfo or UTC).astimezone(UTC)
    except (TypeError, ValueError):
        return None


def parse_since(value: str, reference: dt.datetime | None = None) -> dt.datetime:
    reference = reference or now_utc()
    match = re.fullmatch(r"(\d+)([hd])", value.strip().lower())
    if match:
        amount = int(match.group(1))
        delta = dt.timedelta(hours=amount) if match.group(2) == "h" else dt.timedelta(days=amount)
        return reference - delta
    parsed = parse_time(value)
    if parsed is None:
        raise ValueError("--since must be an ISO timestamp or a duration such as 24h or 7d")
    return parsed


def clean_text(value: Any, limit: int = MAX_TEXT) -> str:
    text = html.unescape(HTML_PATTERN.sub(" ", str(value or "")))
    text = re.sub(r"\s+", " ", text).strip()
    return text[:limit]


def redact(value: str) -> str:
    return SECRET_PATTERN.sub(lambda match: match.group(1) + "[REDACTED]", value)


def stable_id(prefix: str, *parts: Any) -> str:
    material = "\x1f".join(str(part or "") for part in parts)
    return f"{prefix}-{hashlib.sha256(material.encode('utf-8')).hexdigest()[:20]}"


def normalized_text(value: str) -> str:
    return " ".join(WORD_PATTERN.findall(value.lower()))


def content_fingerprint(value: str) -> str:
    return hashlib.sha256(normalized_text(value).encode("utf-8")).hexdigest()


def load_json(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return default


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    records: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        try:
            value = json.loads(line)
            if isinstance(value, dict):
                records.append(value)
        except json.JSONDecodeError:
            continue
    return records


def atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=path.name, dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(content)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def write_json(path: Path, value: Any) -> None:
    atomic_write(path, json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


def write_jsonl(path: Path, records: Iterable[dict[str, Any]]) -> None:
    atomic_write(path, "".join(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n" for record in records))


class StateStore:
    def __init__(self, root: Path):
        self.root = root
        self.root.mkdir(parents=True, exist_ok=True)

    def path(self, name: str) -> Path:
        return self.root / name

    def records(self, name: str) -> list[dict[str, Any]]:
        return load_jsonl(self.path(name))

    def replace_records(self, name: str, records: Iterable[dict[str, Any]]) -> None:
        write_jsonl(self.path(name), records)

    def upsert(self, name: str, new_records: Iterable[dict[str, Any]], key: str = "id") -> int:
        records = self.records(name)
        positions = {record.get(key): index for index, record in enumerate(records) if record.get(key)}
        changed = 0
        for record in new_records:
            identity = record.get(key)
            if not identity:
                continue
            if identity in positions:
                if records[positions[identity]] != record:
                    records[positions[identity]] = record
                    changed += 1
            else:
                positions[identity] = len(records)
                records.append(record)
                changed += 1
        if changed:
            self.replace_records(name, records)
        return changed

    def state(self) -> dict[str, Any]:
        return load_json(self.path("state.json"), {})

    def save_state(self, value: dict[str, Any]) -> None:
        write_json(self.path("state.json"), value)


class Runner(Protocol):
    def __call__(self, args: list[str]) -> subprocess.CompletedProcess[str]: ...


def run_process(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, check=False, capture_output=True, text=True, encoding="utf-8")


@dataclasses.dataclass(frozen=True)
class SourceItem:
    id: str
    source: str
    source_url: str
    thread_url: str
    source_repository: str
    source_type: str
    source_node_id: str
    discussion_node_id: str
    author: str
    published_at: str
    updated_at: str
    title: str
    text: str
    parent_id: str = ""

    def as_record(self) -> dict[str, Any]:
        return dataclasses.asdict(self)


class SourceAdapter(Protocol):
    name: str

    def collect(self, since: dt.datetime) -> tuple[list[SourceItem], list[str]]: ...


GITHUB_QUERY = """
query($owner:String!, $repo:String!, $discussions:Int!, $comments:Int!, $replies:Int!) {
  repository(owner:$owner, name:$repo) {
    discussions(first:$discussions, orderBy:{field:UPDATED_AT,direction:DESC}) {
      nodes {
        id number title body url createdAt updatedAt author { login }
        comments(first:$comments) {
          nodes {
            id body url createdAt updatedAt author { login }
            replies(first:$replies) { nodes { id body url createdAt updatedAt author { login } } }
          }
        }
      }
    }
  }
}
"""


class GitHubDiscussionsAdapter:
    name = "github"

    def __init__(self, config: dict[str, Any], runner: Runner = run_process):
        self.config = config
        self.runner = runner

    def auth(self) -> tuple[bool, str]:
        result = self.runner(["gh", "auth", "status"])
        return result.returncode == 0, clean_text(result.stderr or result.stdout, 300)

    def account(self) -> str:
        result = self.runner(["gh", "api", "user", "--jq", ".login"])
        return clean_text(result.stdout, 100) if result.returncode == 0 else "UNKNOWN"

    def collect(self, since: dt.datetime) -> tuple[list[SourceItem], list[str]]:
        if not self.config.get("enabled", True):
            return [], []
        authenticated, detail = self.auth()
        if not authenticated:
            return [], [f"github: authentication unavailable ({detail or 'gh auth status failed'})"]
        collected: list[SourceItem] = []
        errors: list[str] = []
        discussions_limit = min(max(int(self.config.get("maxDiscussionsPerRepository", 30)), 1), 100)
        comments_limit = min(max(int(self.config.get("maxCommentsPerDiscussion", 50)), 1), 100)
        replies_limit = min(max(int(self.config.get("maxRepliesPerComment", 20)), 1), 100)
        for repository in self.config.get("repositories", []):
            if not repository.get("discussions", True):
                continue
            owner, repo = repository.get("owner"), repository.get("repo")
            if not owner or not repo:
                errors.append("github: skipped malformed repository configuration")
                continue
            args = [
                "gh", "api", "graphql", "-f", f"query={GITHUB_QUERY}",
                "-F", f"owner={owner}", "-F", f"repo={repo}",
                "-F", f"discussions={discussions_limit}", "-F", f"comments={comments_limit}",
                "-F", f"replies={replies_limit}",
            ]
            result = self.runner(args)
            if result.returncode != 0:
                errors.append(f"github:{owner}/{repo}: {clean_text(result.stderr, 300) or 'GraphQL request failed'}")
                continue
            try:
                payload = json.loads(result.stdout)
                discussions = (((payload.get("data") or {}).get("repository") or {}).get("discussions") or {}).get("nodes") or []
            except (json.JSONDecodeError, AttributeError):
                errors.append(f"github:{owner}/{repo}: malformed GraphQL result")
                continue
            for discussion in discussions:
                try:
                    collected.extend(self._map_discussion(owner, repo, discussion, since))
                except (AttributeError, TypeError, ValueError, KeyError):
                    errors.append(f"github:{owner}/{repo}: skipped malformed discussion")
        return collected, errors

    @staticmethod
    def _map_discussion(owner: str, repo: str, discussion: dict[str, Any], since: dt.datetime) -> list[SourceItem]:
        updated = parse_time(discussion.get("updatedAt"))
        if updated is None or updated < since:
            return []
        url = str(discussion["url"])
        node_id = str(discussion["id"])
        title = clean_text(discussion.get("title"), 500)
        repository = f"{owner}/{repo}"
        created = parse_time(discussion.get("createdAt"))
        items = []
        if created and created >= since:
            items.append(SourceItem(
                id=stable_id("src", "github", node_id), source="github", source_url=url, thread_url=url,
                source_repository=repository, source_type="discussion", source_node_id=node_id,
                discussion_node_id=node_id, author=clean_text((discussion.get("author") or {}).get("login"), 100),
                published_at=str(discussion.get("createdAt") or ""), updated_at=str(discussion.get("updatedAt") or ""),
                title=title, text=clean_text(discussion.get("body")),
            ))
        for comment in ((discussion.get("comments") or {}).get("nodes") or []):
            items.extend(GitHubDiscussionsAdapter._map_comment(comment, discussion, repository, url, ""))
            for reply in ((comment.get("replies") or {}).get("nodes") or []):
                items.extend(GitHubDiscussionsAdapter._map_comment(reply, discussion, repository, url, str(comment.get("id") or "")))
        return [item for item in items if (parse_time(item.updated_at) or parse_time(item.published_at) or since) >= since]

    @staticmethod
    def _map_comment(comment: dict[str, Any], discussion: dict[str, Any], repository: str, thread_url: str, parent_id: str) -> list[SourceItem]:
        node_id = str(comment.get("id") or "")
        url = str(comment.get("url") or "")
        if not node_id or not url:
            return []
        return [SourceItem(
            id=stable_id("src", "github", node_id), source="github", source_url=url, thread_url=thread_url,
            source_repository=repository, source_type="comment", source_node_id=node_id,
            discussion_node_id=str(discussion.get("id") or ""),
            author=clean_text((comment.get("author") or {}).get("login"), 100),
            published_at=str(comment.get("createdAt") or ""), updated_at=str(comment.get("updatedAt") or ""),
            title=clean_text(discussion.get("title"), 500), text=clean_text(comment.get("body")), parent_id=parent_id,
        )]


class HackerNewsAdapter:
    name = "hackernews"
    base = "https://hacker-news.firebaseio.com/v0"

    def __init__(self, config: dict[str, Any], opener: Callable[..., Any] = urllib.request.urlopen):
        self.config = config
        self.opener = opener

    def _get(self, path: str) -> Any:
        request = urllib.request.Request(f"{self.base}/{path}.json", headers={"User-Agent": "mcp-compass-feedback/0.1"})
        with self.opener(request, timeout=15) as response:
            return json.loads(response.read().decode("utf-8"))

    def fetch_user(self, username: str) -> dict[str, Any] | None:
        """Return bounded public HN user metadata from the official user/{id} endpoint."""
        value = self._get(f"user/{username}")
        if not isinstance(value, dict):
            return None
        return {
            "id": clean_text(value.get("id"), 100),
            "created": value.get("created"),
            "karma": value.get("karma"),
            "about": clean_text(value.get("about"), 500),
        }

    def collect(self, since: dt.datetime) -> tuple[list[SourceItem], list[str]]:
        if not self.config.get("enabled", True):
            return [], []
        limit = min(max(int(self.config.get("maxItemsPerFeed", 30)), 1), 100)
        comment_limit = min(max(int(self.config.get("maxCommentsPerStory", 40)), 0), 200)
        ids: list[int] = []
        errors: list[str] = []
        for feed in self.config.get("feeds", ["newstories"]):
            if feed not in {"newstories", "showstories", "askstories"}:
                errors.append(f"hackernews: ignored unsupported feed {feed}")
                continue
            try:
                values = self._get(feed) or []
                ids.extend(int(value) for value in values[:limit])
            except (OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
                errors.append(f"hackernews:{feed}: {clean_text(exc, 200)}")
        collected: list[SourceItem] = []
        for story_id in dict.fromkeys(ids):
            try:
                story = self._get(f"item/{story_id}")
                if not isinstance(story, dict) or story.get("deleted") or story.get("dead"):
                    continue
                story_time = dt.datetime.fromtimestamp(int(story.get("time", 0)), UTC)
                if story_time < since:
                    continue
                collected.append(self._map_item(story, story, "hn_story"))
                queue = deque(story.get("kids") or [])
                seen = 0
                while queue and seen < comment_limit:
                    comment = self._get(f"item/{int(queue.popleft())}")
                    seen += 1
                    if not isinstance(comment, dict) or comment.get("deleted") or comment.get("dead"):
                        continue
                    queue.extend(comment.get("kids") or [])
                    comment_time = dt.datetime.fromtimestamp(int(comment.get("time", 0)), UTC)
                    if comment_time >= since:
                        collected.append(self._map_item(comment, story, "hn_comment"))
            except (OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
                errors.append(f"hackernews:item/{story_id}: {clean_text(exc, 200)}")
        return collected, errors

    @staticmethod
    def _map_item(item: dict[str, Any], story: dict[str, Any], source_type: str) -> SourceItem:
        item_id = str(item["id"])
        story_id = str(story["id"])
        url = f"https://news.ycombinator.com/item?id={item_id}"
        thread_url = f"https://news.ycombinator.com/item?id={story_id}"
        timestamp = iso(dt.datetime.fromtimestamp(int(item.get("time", 0)), UTC))
        return SourceItem(
            id=stable_id("src", "hackernews", item_id), source="hackernews", source_url=url,
            thread_url=thread_url, source_repository="", source_type=source_type, source_node_id=item_id,
            discussion_node_id=story_id, author=clean_text(item.get("by"), 100), published_at=timestamp,
            updated_at=timestamp, title=clean_text(story.get("title"), 500),
            text=clean_text(item.get("text") or item.get("title")), parent_id=str(item.get("parent") or ""),
        )


class RedditAdapter:
    name = "reddit"

    def __init__(self, config: dict[str, Any]):
        self.config = config

    def status(self) -> str:
        if not self.config.get("enabled", False):
            return "DISABLED_NOT_CONFIGURED"
        if not os.environ.get("REDDIT_CLIENT_ID") or not os.environ.get("REDDIT_CLIENT_SECRET"):
            return "DISABLED_NO_CREDENTIALS"
        return "ENABLED"

    def collect(self, since: dt.datetime) -> tuple[list[SourceItem], list[str]]:
        del since
        state = self.status()
        if state == "ENABLED":
            return [], ["reddit: official authenticated read adapter is not implemented in V0.1"]
        return [], []


def relevance_score(item: dict[str, Any]) -> float:
    text = normalized_text(f"{item.get('title', '')} {item.get('text', '')}")
    if not text or any(marker in text for marker in PROMO_MARKERS):
        return 0.05
    hits = {name: sum(1 for term in terms if normalized_text(term) in text) for name, terms in CONCEPTS.items()}
    score = 0.05
    if hits["mcp"]:
        score += 0.38
    score += min(0.42, 0.12 * sum(1 for name in ("discovery", "security", "validation", "reliability", "authentication", "generation", "ranking") if hits[name]))
    score += min(0.12, 0.04 * sum(hits.values()))
    if hits["mcp"] and any(hits[name] for name in ("discovery", "security", "validation", "reliability", "authentication", "generation", "ranking")):
        score += 0.15
    if hits["builder"]:
        score += 0.08
    if "ai" in text and not hits["mcp"] and sum(hits.values()) == 0:
        score = min(score, 0.15)
    return round(min(score, 1.0), 2)


def classify_problem(text: str) -> dict[str, Any]:
    normalized = normalized_text(text)
    scored = []
    for rule in PROBLEM_RULES:
        score = sum(1 for term in rule["terms"] if normalized_text(term) in normalized)
        scored.append((score, rule))
    score, rule = max(scored, key=lambda pair: pair[0])
    if score == 0:
        return {
            "key": "mcp-ecosystem-friction",
            "title": "General MCP ecosystem friction",
            "statement": "Developers report friction evaluating or integrating MCP servers.",
            "area": "ecosystem",
            "features": ("further problem discovery",),
        }
    return rule


def potential_user_score(item: dict[str, Any], relevance: float, reference: dt.datetime | None = None) -> int:
    reference = reference or now_utc()
    text = normalized_text(f"{item.get('title', '')} {item.get('text', '')}")
    score = int(relevance * 45)
    positive = {
        "building an agent": 18, "build an agent": 14, "using mcp": 12, "integrating mcp": 14,
        "mcp server": 8, "selecting": 8, "read-only": 10, "security": 8, "openapi": 8,
        "we need": 8, "i need": 8, "maintain": 6,
    }
    score += sum(weight for term, weight in positive.items() if term in text)
    if any(term in text for term in ("generic ai", "thought leadership", "marketing agency")):
        score -= 25
    published = parse_time(str(item.get("published_at") or ""))
    if published and reference - published <= dt.timedelta(days=7):
        score += 8
    elif published and reference - published > dt.timedelta(days=90):
        score -= 15
    return max(0, min(100, score))


def summarize(text: str, limit: int = 280) -> str:
    value = redact(clean_text(text, limit + 100))
    if len(value) <= limit:
        return value
    return value[: limit - 1].rstrip() + "…"


def evidence_from_item(item: dict[str, Any], reference: dt.datetime | None = None) -> dict[str, Any]:
    combined = f"{item.get('title', '')} {item.get('text', '')}"
    relevance = relevance_score(item)
    rule = classify_problem(combined)
    urgent = any(marker in normalized_text(combined) for marker in URGENT_MARKERS)
    excerpt = summarize(str(item.get("text") or item.get("title") or ""), 500)
    return {
        "id": stable_id("ev", item.get("source"), item.get("source_node_id") or item.get("source_url")),
        "sourceItemId": item.get("id"), "source": item.get("source"), "sourceUrl": item.get("source_url"),
        "threadUrl": item.get("thread_url"), "sourceRepository": item.get("source_repository"),
        "sourceType": item.get("source_type"), "sourceNodeId": item.get("source_node_id"),
        "discussionNodeId": item.get("discussion_node_id"), "parentId": item.get("parent_id"),
        "author": item.get("author") or "unknown", "publishedAt": item.get("published_at"),
        "title": summarize(str(item.get("title") or ""), 300), "excerpt": excerpt,
        "summary": summarize(excerpt, 240), "problemKey": rule["key"], "problemStatement": rule["statement"],
        "problemTitle": rule["title"], "productArea": rule["area"], "evidenceType": "developer-feedback",
        "relevanceScore": relevance, "confidence": round(min(0.98, 0.55 + relevance * 0.42), 2),
        "potentialUserScore": potential_user_score(item, relevance, reference),
        "featureCandidates": list(rule["features"]), "status": "URGENT_REVIEW" if urgent else "new",
        "contentFingerprint": content_fingerprint(excerpt), "observedAt": iso(reference or now_utc()),
    }


def is_duplicate(candidate: dict[str, Any], existing: Iterable[dict[str, Any]]) -> bool:
    candidate_normalized = normalized_text(str(candidate.get("excerpt") or ""))
    for record in existing:
        if candidate.get("sourceUrl") and candidate.get("sourceUrl") == record.get("sourceUrl"):
            return True
        if candidate.get("contentFingerprint") and candidate.get("contentFingerprint") == record.get("contentFingerprint"):
            return True
        other = normalized_text(str(record.get("excerpt") or ""))
        if len(candidate_normalized) >= 80 and len(other) >= 80 and (candidate_normalized in other or other in candidate_normalized):
            return True
    return False


def build_clusters(evidence: list[dict[str, Any]], thresholds: dict[str, Any], reference: dt.datetime | None = None) -> list[dict[str, Any]]:
    reference = reference or now_utc()
    groups: dict[str, list[dict[str, Any]]] = {}
    for record in evidence:
        groups.setdefault(str(record.get("problemKey")), []).append(record)
    clusters: list[dict[str, Any]] = []
    for key, records in groups.items():
        authors = {str(record.get("author") or "unknown").lower() for record in records}
        threads = {str(record.get("threadUrl") or record.get("sourceUrl")) for record in records}
        sources = {str(record.get("source")) for record in records}
        independent = {
            str(record.get("author") or "").lower() or f"unknown@{record.get('threadUrl') or record.get('sourceUrl')}"
            for record in records
        }
        dates = [parse_time(str(record.get("publishedAt") or "")) for record in records]
        dates = [value for value in dates if value]
        recent7 = sum(1 for value in dates if reference - dt.timedelta(days=7) <= value <= reference)
        prior7 = sum(1 for value in dates if reference - dt.timedelta(days=14) <= value < reference - dt.timedelta(days=7))
        recent30 = sum(1 for value in dates if reference - dt.timedelta(days=30) <= value <= reference)
        prior30 = sum(1 for value in dates if reference - dt.timedelta(days=60) <= value < reference - dt.timedelta(days=30))
        urgent = any(record.get("status") == "URGENT_REVIEW" for record in records)
        qualifies = len(authors) >= int(thresholds.get("roadmapIndependentAuthors", 3)) and len(threads) >= int(thresholds.get("roadmapIndependentThreads", 2))
        sample = len(independent) < 5
        clusters.append({
            "problemKey": key, "title": records[0].get("problemTitle"), "description": records[0].get("problemStatement"),
            "evidenceCount": len(records), "independentEvidenceCount": len(independent), "uniqueAuthors": len(authors),
            "uniqueThreads": len(threads), "uniqueSources": len(sources), "firstSeen": iso(min(dates)) if dates else None,
            "lastSeen": iso(max(dates)) if dates else None, "productArea": records[0].get("productArea"),
            "roadmapStatus": "URGENT_REVIEW" if urgent else ("candidate" if qualifies else "insufficient-evidence"),
            "change7d": recent7 - prior7, "change30d": recent30 - prior30, "smallSample": sample,
            "featureCandidates": sorted({feature for record in records for feature in record.get("featureCandidates", [])}),
            "exampleEvidenceUrls": list(dict.fromkeys(str(record.get("sourceUrl")) for record in records if record.get("sourceUrl")))[:5],
        })
    return sorted(clusters, key=lambda value: (-int(value["independentEvidenceCount"]), str(value["problemKey"])))


def user_level(score: int) -> str:
    return "HIGH" if score >= 80 else ("MEDIUM" if score >= 60 else "LOW")


def collect_command(config: dict[str, Any], store: StateStore, since: dt.datetime, runner: Runner = run_process, opener: Callable[..., Any] = urllib.request.urlopen) -> dict[str, Any]:
    adapters: list[SourceAdapter] = [
        GitHubDiscussionsAdapter(config.get("github", {}), runner),
        HackerNewsAdapter(config.get("hackerNews", {}), opener),
        RedditAdapter(config.get("reddit", {})),
    ]
    items: list[dict[str, Any]] = []
    errors: list[str] = []
    counts: dict[str, int] = {}
    for adapter in adapters:
        try:
            found, adapter_errors = adapter.collect(since)
            records = [item.as_record() for item in found]
            items.extend(records)
            errors.extend(adapter_errors)
            counts[adapter.name] = len(records)
        except Exception as exc:  # isolate malformed/unavailable external sources
            errors.append(f"{adapter.name}: {clean_text(exc, 200)}")
            counts[adapter.name] = 0
    engagements = store.records("engagements.jsonl")
    engagement_by_comment = {record.get("commentId"): record for record in engagements if record.get("commentId")}
    responses: dict[str, list[dict[str, Any]]] = {}
    for item in items:
        engagement = engagement_by_comment.get(item.get("parent_id"))
        if engagement:
            item["engagementId"] = engagement.get("id")
            item["isEngagementResponse"] = True
            responses.setdefault(str(engagement.get("id")), []).append(item)
    if responses:
        for engagement in engagements:
            matching = responses.get(str(engagement.get("id")), [])
            if matching:
                engagement["responseCount"] = len(matching)
                engagement["lastResponseAt"] = max(
                    str(item.get("updated_at") or item.get("published_at") or "") for item in matching
                )
                engagement["status"] = "RESPONSE_RECEIVED"
        store.replace_records("engagements.jsonl", engagements)
    changed = store.upsert("inbox.jsonl", items)
    state = store.state()
    state.update({
        "lastCollectionAt": iso(now_utc()), "lastCollectionSince": iso(since), "lastCollectionCounts": counts,
        "lastCollectionErrors": errors, "redditStatus": RedditAdapter(config.get("reddit", {})).status(),
        "engagementResponsesFound": sum(len(value) for value in responses.values()),
    })
    authenticated, _ = GitHubDiscussionsAdapter(config.get("github", {}), runner).auth()
    state["githubPublishing"] = "AVAILABLE" if authenticated else "PUBLISHING_UNAVAILABLE"
    store.save_state(state)
    return {"collected": len(items), "changed": changed, "counts": counts, "errors": errors}


def analyse_command(config: dict[str, Any], store: StateStore, reference: dt.datetime | None = None) -> dict[str, Any]:
    threshold = float((config.get("thresholds") or {}).get("collectRelevance", 0.60))
    existing = store.records("evidence.jsonl")
    accepted: list[dict[str, Any]] = []
    ignored = 0
    for item in store.records("inbox.jsonl"):
        try:
            evidence = evidence_from_item(item, reference)
            if float(evidence["relevanceScore"]) < threshold:
                ignored += 1
                continue
            if is_duplicate(evidence, [*existing, *accepted]):
                continue
            accepted.append(evidence)
        except (TypeError, ValueError, KeyError):
            ignored += 1
    store.upsert("evidence.jsonl", accepted)
    all_evidence = store.records("evidence.jsonl")
    clusters = build_clusters(all_evidence, config.get("thresholds", {}), reference)
    write_json(store.path("clusters.json"), clusters)
    return {"newEvidence": len(accepted), "ignored": ignored, "totalEvidence": len(all_evidence), "clusters": len(clusters)}


def eligible_opportunities(config: dict[str, Any], store: StateStore, limit: int = 5) -> list[dict[str, Any]]:
    threshold = float((config.get("thresholds") or {}).get("engagementRelevance", 0.75))
    evidence = [
        record for record in store.records("evidence.jsonl")
        if float(record.get("relevanceScore", 0)) >= threshold
        and (int(record.get("potentialUserScore", 0)) >= 60 or record.get("status") == "URGENT_REVIEW")
    ]
    evidence.sort(key=lambda value: (-int(value.get("potentialUserScore", 0)), -float(value.get("relevanceScore", 0)), str(value.get("id"))))
    return evidence[: max(0, min(limit, 5))]


def report_command(config: dict[str, Any], store: StateStore, output: Path | None = None, reference: dt.datetime | None = None) -> tuple[Path, Path]:
    reference = reference or now_utc()
    evidence = store.records("evidence.jsonl")
    clusters = load_json(store.path("clusters.json"), [])
    opportunities = eligible_opportunities(config, store, 5)
    published_dates = [parse_time(str(item.get("publishedAt"))) for item in evidence]
    start = min((value for value in published_dates if value is not None), default=None)
    lines = [
        "# MCP Compass Feedback Intelligence", "", f"Period: {iso(start) if start else 'No evidence'} → {iso(reference)}", "",
        "## Summary", "", f"Relevant evidence items: {len(evidence)}", f"Problem clusters: {len(clusters)}",
        f"High-potential users: {sum(1 for item in evidence if int(item.get('potentialUserScore', 0)) >= 80)}",
        f"Reply opportunities: {len(opportunities)}", "", "## Top recurring problems", "",
    ]
    for index, cluster in enumerate(clusters[:10], 1):
        trend = f"7-day change {cluster['change7d']:+d}; 30-day change {cluster['change30d']:+d}"
        if cluster.get("smallSample"):
            trend += " (small sample; not statistically significant)"
        lines.extend([
            f"{index}. **{redact(str(cluster.get('title')))}**", f"   Evidence: {cluster.get('evidenceCount')}; independent: {cluster.get('independentEvidenceCount')}; "
            f"authors: {cluster.get('uniqueAuthors')}; threads: {cluster.get('uniqueThreads')}; platforms: {cluster.get('uniqueSources')}.",
            f"   Trend: {trend}. Roadmap status: `{cluster.get('roadmapStatus')}`.", "",
        ])
    lines.extend(["## Engagement opportunities", ""])
    if not opportunities:
        lines.extend(["No discussions worth responding to in the current evidence set.", ""])
    for index, item in enumerate(opportunities, 1):
        lines.extend([
            f"### {index}. {redact(str(item.get('title') or item.get('problemTitle')))}", "",
            f"Target: {item.get('sourceUrl')}", f"Why relevant: {redact(str(item.get('problemStatement')))}",
            f"Potential user: {user_level(int(item.get('potentialUserScore', 0)))} ({item.get('potentialUserScore')})",
            f"Evidence: `{item.get('problemKey')}`", f"Suggested action: run `draft {item.get('id')}`; do not publish.", "",
        ])
    lines.extend(["## New potential users", ""])
    potential_users = sorted(evidence, key=lambda item: -int(item.get("potentialUserScore", 0)))[:5]
    if not potential_users or int(potential_users[0].get("potentialUserScore", 0)) < 60:
        lines.extend(["No medium- or high-potential users identified in the current evidence set.", ""])
    else:
        for item in potential_users:
            score = int(item.get("potentialUserScore", 0))
            if score < 60:
                continue
            lines.append(f"- {redact(str(item.get('author')))} — {user_level(score)} ({score}); {item.get('sourceUrl')}")
        lines.append("")
    lines.extend(["## Emerging feature candidates", ""])
    for cluster in clusters[:10]:
        features = ", ".join(cluster.get("featureCandidates", [])) or "needs further discovery"
        lines.append(
            f"- **{redact(str(cluster.get('title')))}:** {features}. Problem status: `{cluster.get('roadmapStatus')}`; solutions are not commitments."
        )
    if not clusters:
        lines.append("- No evidence-backed candidates.")
    lines.append("")
    below = [cluster for cluster in clusters if cluster.get("roadmapStatus") == "insufficient-evidence"]
    lines.extend(["## What not to build yet", ""])
    lines.extend([f"- {redact(str(cluster.get('title')))} — {cluster.get('uniqueAuthors')} author(s), {cluster.get('uniqueThreads')} thread(s)." for cluster in below] or ["- No below-threshold clusters."])
    report_path = output or store.path("reports") / f"feedback-intelligence-{reference.date().isoformat()}.md"
    atomic_write(report_path, "\n".join(lines).rstrip() + "\n")

    candidates = ["# MCP Compass Roadmap Candidates", "", "Recommendations only; an operator decides whether to create a `PLANS.md` task.", ""]
    selected = [cluster for cluster in clusters if cluster.get("roadmapStatus") in {"candidate", "URGENT_REVIEW"}]
    if not selected:
        candidates.extend(["No problem cluster currently meets the configured roadmap threshold.", ""])
    for cluster in selected:
        candidates.extend([
            f"## {redact(str(cluster.get('title')))}", "", f"Problem: {redact(str(cluster.get('description')))}",
            f"Evidence: {cluster.get('independentEvidenceCount')} independent reports; {cluster.get('uniqueAuthors')} authors; {cluster.get('uniqueThreads')} threads; {cluster.get('uniqueSources')} platforms.",
            f"Product area: {cluster.get('productArea')}", f"Severity/review: {cluster.get('roadmapStatus')}",
            f"Trend: 7-day {cluster.get('change7d'):+d}; 30-day {cluster.get('change30d'):+d}{' (small sample)' if cluster.get('smallSample') else ''}.",
            "Possible solutions: " + ", ".join(cluster.get("featureCandidates", [])), "Example evidence:",
            *[f"- {url}" for url in cluster.get("exampleEvidenceUrls", [])],
            "Recommendation: review the underlying problem; do not automatically implement a feature.", "",
        ])
    roadmap_path = report_path.parent / "roadmap-candidates.md"
    atomic_write(roadmap_path, "\n".join(candidates).rstrip() + "\n")
    return report_path, roadmap_path


def proposal_hash(proposal: dict[str, Any]) -> str:
    exact = {key: proposal.get(key, "") for key in ("targetUrl", "operation", "discussionNodeId", "replyToId", "body")}
    return hashlib.sha256(json.dumps(exact, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()


def draft_body(evidence: dict[str, Any]) -> str:
    key = evidence.get("problemKey")
    templates = {
        "forbidden-capability-verification": "The distinction between declared and enforced capabilities seems important here. `tools/list` and input schemas provide stronger evidence than Registry metadata, but they still do not prove that a prohibited write path is unreachable for arbitrary inputs. How are you validating the underlying permission boundary today?",
        "server-discovery-selection": "A useful comparison seems to start from the agent requirement rather than the server name: required tools, forbidden capabilities, authentication constraints, and the evidence behind each match. Which part of the selection process is currently most manual for you?",
        "server-trust-security": "It may help to separate self-declared metadata, repository-derived evidence, schema-inferred risk, and runtime-observed behavior instead of collapsing them into one trust score. Which evidence would materially change whether you used this server?",
        "dead-unreachable-servers": "A directory entry is not enough to establish installability or reachability. Release freshness, reproducible setup, endpoint health, and the timestamp of each check should probably be shown separately. Which failure did you hit first: installation, authentication, or protocol startup?",
        "authentication-requirements-unclear": "Authentication should be part of server selection rather than a surprise after installation. It would be useful to distinguish required credential type, setup steps, and whether anonymous/local operation is possible. What auth requirement was missing from the listing?",
        "tool-schema-compatibility": "Comparing the normalized `tools/list` schemas and client constraints may be more reliable than comparing package descriptions. Do you have a minimal schema or client interaction that demonstrates the incompatibility?",
        "openapi-mcp-generation": "A contract-first path can keep generation reviewable: select operations, define tool names and inputs, classify mutating behavior, then generate code from the approved contract. Which part is hardest in your case: endpoint selection, auth mapping, or tool schema design?",
        "mcp-runtime-validation": "Runtime checks are most useful when they are described as bounded observations rather than certification: protocol startup, `tools/list`, schema comparison, and sandbox controls. What behavior would you need to observe before trusting the result?",
    }
    return templates.get(str(key), "This sounds like a concrete MCP integration problem rather than a generic feature request. What requirement, observed behavior, and expected boundary would make the problem reproducible?")


def create_proposal(store: StateStore, evidence_id: str, body: str | None = None, account: str = "UNKNOWN") -> dict[str, Any]:
    evidence = next((record for record in store.records("evidence.jsonl") if record.get("id") == evidence_id), None)
    if evidence is None:
        raise ValueError(f"Unknown evidence ID: {evidence_id}")
    source = evidence.get("source")
    operation = "MANUAL_POST" if source == "hackernews" else ("REPLY" if evidence.get("sourceType") == "comment" else "CREATE")
    final_body = clean_text(body or draft_body(evidence), 5000)
    proposal = {
        "evidenceId": evidence_id, "source": source, "repository": evidence.get("sourceRepository"),
        "discussionTitle": evidence.get("title"), "targetUrl": evidence.get("sourceUrl"),
        "discussionNodeId": evidence.get("discussionNodeId"),
        "replyToId": evidence.get("sourceNodeId") if operation == "REPLY" else "", "operation": operation,
        "account": account, "why": evidence.get("problemStatement"), "problemKey": evidence.get("problemKey"),
        "technicalValue": summarize(final_body.split("?")[0], 240),
        "shouldMentionMcpCompass": False,
        "usefulWithoutPromotion": "mcp compass" not in final_body.lower() and not URL_PATTERN.search(final_body),
        "potentialUser": user_level(int(evidence.get("potentialUserScore", 0))), "body": final_body,
        "mentionsMcpCompass": "mcp compass" in final_body.lower(),
        "containsProductLink": any("mcp-compass" in url.lower() for url in URL_PATTERN.findall(final_body)),
        "createdAt": iso(now_utc()), "status": "DRAFT",
    }
    proposal["hash"] = proposal_hash(proposal)
    proposal["id"] = stable_id("proposal", proposal["hash"])
    store.upsert("proposals.jsonl", [proposal])
    return proposal


def format_proposal(proposal: dict[str, Any]) -> str:
    action = "MANUAL HN POST" if proposal.get("operation") == "MANUAL_POST" else f"POST DISCUSSION {proposal.get('operation')}"
    return "\n".join([
        "COMMUNITY REPLY PROPOSAL", "", f"Proposal: {proposal.get('id')}", f"Content hash: {proposal.get('hash')}",
        f"Source: {'Hacker News' if proposal.get('source') == 'hackernews' else 'GitHub Discussions'}",
        f"Repository: {proposal.get('repository') or 'N/A'}", f"Discussion: {proposal.get('discussionTitle')}",
        f"Target: {proposal.get('targetUrl')}", f"Account: {proposal.get('account')}",
        f"Why this matters: {proposal.get('why')}", f"Evidence: {proposal.get('problemKey')}",
        f"Potential user: {proposal.get('potentialUser')}", "", "Proposed final reply:", "---",
        str(proposal.get("body")), "---", "", f"Mentions MCP Compass: {'YES' if proposal.get('mentionsMcpCompass') else 'NO'}",
        f"Contains product link: {'YES' if proposal.get('containsProductLink') else 'NO'}", f"Action requested: {action}",
    ])


def approve_proposal(store: StateStore, proposal_id: str, expected_hash: str, reference: dt.datetime | None = None) -> dict[str, Any]:
    proposal = next((record for record in store.records("proposals.jsonl") if record.get("id") == proposal_id), None)
    if proposal is None:
        raise ValueError(f"Unknown proposal ID: {proposal_id}")
    if proposal.get("operation") == "MANUAL_POST":
        raise ValueError("Hacker News publishing is manual; no publish approval is recorded")
    prior_attempts = [record for record in store.records("engagements.jsonl") if record.get("proposalId") == proposal_id]
    if prior_attempts:
        raise ValueError("This proposal already has a publication attempt; reconcile it manually instead of retrying")
    actual = proposal_hash(proposal)
    if actual != proposal.get("hash") or actual != expected_hash:
        raise ValueError("Proposal content, target, or operation changed; approval is invalid")
    approved_at = reference or now_utc()
    approval = {
        "id": stable_id("approval", proposal_id, actual, iso(approved_at)), "proposalId": proposal_id,
        "proposalHash": actual, "approvedAt": iso(approved_at),
        "expiresAt": iso(approved_at + dt.timedelta(minutes=APPROVAL_MINUTES)), "consumedAt": None,
    }
    store.upsert("approvals.jsonl", [approval])
    return approval


class GitHubPublisher:
    mutation = """
mutation($discussionId:ID!, $body:String!, $replyToId:ID) {
  addDiscussionComment(input:{discussionId:$discussionId, body:$body, replyToId:$replyToId}) {
    comment { id url }
  }
}
"""

    def __init__(self, runner: Runner = run_process):
        self.runner = runner

    def check_auth(self) -> None:
        auth = self.runner(["gh", "auth", "status"])
        if auth.returncode != 0:
            raise RuntimeError("PUBLISHING_UNAVAILABLE: gh auth status did not report a usable identity")

    def publish(self, proposal: dict[str, Any]) -> dict[str, str]:
        args = [
            "gh", "api", "graphql", "-f", f"query={self.mutation}",
            "-F", f"discussionId={proposal.get('discussionNodeId')}", "-f", f"body={proposal.get('body')}",
        ]
        if proposal.get("operation") == "REPLY":
            args.extend(["-F", f"replyToId={proposal.get('replyToId')}"])
        result = self.runner(args)
        if result.returncode != 0:
            raise RuntimeError(f"GitHub mutation failed: {clean_text(result.stderr, 300)}")
        try:
            comment = json.loads(result.stdout)["data"]["addDiscussionComment"]["comment"]
            return {"id": str(comment["id"]), "url": str(comment["url"])}
        except (json.JSONDecodeError, KeyError, TypeError) as exc:
            raise RuntimeError("GitHub mutation returned no comment ID/URL") from exc


def publish_proposal(store: StateStore, proposal_id: str, publisher: GitHubPublisher, reference: dt.datetime | None = None) -> dict[str, Any]:
    reference = reference or now_utc()
    proposals = store.records("proposals.jsonl")
    proposal = next((record for record in proposals if record.get("id") == proposal_id), None)
    if proposal is None:
        raise ValueError(f"Unknown proposal ID: {proposal_id}")
    if proposal.get("source") != "github" or proposal.get("operation") not in {"CREATE", "REPLY"}:
        raise ValueError("Only approved GitHub Discussion create/reply operations can publish")
    actual = proposal_hash(proposal)
    if actual != proposal.get("hash"):
        raise ValueError("Proposal content, target, or operation changed; approval is invalid")
    approvals = [record for record in store.records("approvals.jsonl") if record.get("proposalId") == proposal_id and not record.get("consumedAt")]
    approval = approvals[-1] if approvals else None
    if approval is None or approval.get("proposalHash") != actual:
        raise ValueError("Explicit approval for this exact proposal is required immediately before publishing")
    if (parse_time(str(approval.get("expiresAt"))) or dt.datetime.min.replace(tzinfo=UTC)) < reference:
        raise ValueError("Approval expired; show the exact package and obtain fresh approval")
    if hasattr(publisher, "check_auth"):
        publisher.check_auth()
    approval["consumedAt"] = iso(reference)
    all_approvals = store.records("approvals.jsonl")
    for index, record in enumerate(all_approvals):
        if record.get("id") == approval.get("id"):
            all_approvals[index] = approval
    store.replace_records("approvals.jsonl", all_approvals)
    engagement = {
        "id": stable_id("engagement", proposal_id, actual), "proposalId": proposal_id,
        "source": "github", "operation": proposal.get("operation"), "targetUrl": proposal.get("targetUrl"),
        "commentId": "", "commentUrl": "", "attemptedAt": iso(reference), "status": "PUBLISHING",
    }
    store.upsert("engagements.jsonl", [engagement])
    try:
        result = publisher.publish(proposal)
    except Exception as exc:
        engagement["status"] = "PUBLISH_FAILED_OR_UNCERTAIN"
        engagement["error"] = redact(clean_text(exc, 300))
        store.upsert("engagements.jsonl", [engagement])
        raise
    engagement.update({
        "commentId": result["id"], "commentUrl": result["url"],
        "publishedAt": iso(reference), "status": "POSTED",
    })
    store.upsert("engagements.jsonl", [engagement])
    return engagement


def load_config(path: Path) -> dict[str, Any]:
    value = load_json(path, None)
    if not isinstance(value, dict):
        raise ValueError(f"Invalid source configuration: {path}")
    return value


def print_json(value: Any) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--state-dir", type=Path, default=DEFAULT_STATE)
    subparsers = parser.add_subparsers(dest="command", required=True)
    collect = subparsers.add_parser("collect", help="Collect bounded recent public material")
    collect.add_argument("--since", default="24h")
    subparsers.add_parser("analyse", help="Filter, extract, deduplicate, cluster, and score evidence")
    report = subparsers.add_parser("report", help="Generate intelligence and roadmap-candidate reports")
    report.add_argument("--output", type=Path)
    opportunities = subparsers.add_parser("opportunities", help="List up to five high-value participation opportunities")
    opportunities.add_argument("--limit", type=int, default=5)
    draft = subparsers.add_parser("draft", help="Prepare one reply proposal without publishing")
    draft.add_argument("evidence_id")
    draft.add_argument("--body-file", type=Path)
    approve = subparsers.add_parser("approve", help="Record operator approval after exact package approval in conversation")
    approve.add_argument("proposal_id")
    approve.add_argument("--hash", required=True, dest="expected_hash")
    publish = subparsers.add_parser("publish", help="Publish exactly one approved GitHub Discussion comment")
    publish.add_argument("proposal_id")
    subparsers.add_parser("status", help="Show source and publishing availability without collecting")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        config = load_config(args.config)
        store = StateStore(args.state_dir)
        if args.command == "collect":
            print_json(collect_command(config, store, parse_since(args.since)))
        elif args.command == "analyse":
            print_json(analyse_command(config, store))
        elif args.command == "report":
            report, roadmap = report_command(config, store, args.output)
            print_json({"report": str(report), "roadmapCandidates": str(roadmap)})
        elif args.command == "opportunities":
            print_json(eligible_opportunities(config, store, args.limit))
        elif args.command == "draft":
            body = args.body_file.read_text(encoding="utf-8") if args.body_file else None
            github = GitHubDiscussionsAdapter(config.get("github", {}))
            print(format_proposal(create_proposal(store, args.evidence_id, body, github.account())))
        elif args.command == "approve":
            print_json(approve_proposal(store, args.proposal_id, args.expected_hash))
        elif args.command == "publish":
            print_json(publish_proposal(store, args.proposal_id, GitHubPublisher()))
        elif args.command == "status":
            github = GitHubDiscussionsAdapter(config.get("github", {}))
            authenticated, _ = github.auth()
            print_json({
                "githubPublishing": "AVAILABLE" if authenticated else "PUBLISHING_UNAVAILABLE",
                "githubAccount": github.account() if authenticated else "UNKNOWN",
                "hackerNews": "READ_ONLY" if config.get("hackerNews", {}).get("enabled", True) else "DISABLED_NOT_CONFIGURED",
                "reddit": RedditAdapter(config.get("reddit", {})).status(),
                "stateDirectory": str(store.root),
            })
        return 0
    except (OSError, ValueError, RuntimeError, urllib.error.URLError) as exc:
        print(redact(str(exc)), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
