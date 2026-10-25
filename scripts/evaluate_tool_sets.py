"""Reproducible EVI-08 proposal evaluation; no MCP server is contacted or run."""

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "backend/src/test/resources/fixtures/ranking/registry-relevance-v1.json"
STOP = {"a", "and", "as", "by", "for", "from", "in", "into", "of", "on", "or", "the", "to", "with", "without", "use", "read", "manage"}


def tool(name, description, *fields):
    return {"name": name, "description": description, "inputSchema": {
        "type": "object", "properties": {field: {"type": "string", "description": f"{field} for {description.lower()}"} for field in fields},
        "required": list(fields), "additionalProperties": False}}


# These are authored scenarios, not observed Registry tools. Gold sets are task steps,
# so a partial set is counted as a missing complement even when it contains one useful tool.
CASES = [
    ("github-pr-analysis", [tool("get_issue", "Read GitHub issue", "issue_number"), tool("get_pull_request", "Read GitHub pull request", "pr_number"), tool("analyze_pull_request", "Analyze GitHub pull request changes", "pr_number"), tool("search_developers", "Find GitHub developers", "skill")], ["get_issue", "get_pull_request", "analyze_pull_request"], []),
    ("github-workflow-automation", [tool("list_repositories", "List GitHub repositories", "owner"), tool("manage_release", "Manage GitHub release", "tag"), tool("run_workflow", "Run GitHub workflow", "workflow_id"), tool("get_workflow_logs", "Read GitHub workflow logs", "run_id")], ["list_repositories", "manage_release", "run_workflow"], []),
    ("twilio-message-history", [tool("send_sms", "Send Twilio SMS message", "to", "body"), tool("list_messages", "List previous Twilio messages", "phone_number"), tool("create_call", "Create Twilio voice call", "to")], ["send_sms", "list_messages"], []),
    ("postgres-dba-analysis", [tool("list_slow_queries", "Analyze PostgreSQL slow queries", "database"), tool("inspect_vacuum", "Analyze PostgreSQL vacuum and bloat", "table"), tool("inspect_locks", "Analyze PostgreSQL blocking locks", "database"), tool("execute_sql", "Execute arbitrary PostgreSQL SQL", "sql")], ["list_slow_queries", "inspect_vacuum", "inspect_locks"], []),
    ("postgres-safe-write", [tool("preview_write", "Preview PostgreSQL write", "sql"), tool("execute_write", "Execute PostgreSQL write after preview", "preview_id"), tool("rollback_transaction", "Rollback PostgreSQL transaction", "transaction_id"), tool("select_rows", "Read PostgreSQL rows", "table")], ["preview_write", "execute_write", "rollback_transaction"], []),
    ("web-docs-readonly", [tool("search_docs", "Search web documentation", "query"), tool("fetch_markdown", "Fetch documentation page as Markdown", "url"), tool("publish_docs", "Publish documentation page", "body")], ["search_docs", "fetch_markdown"], ["publish_docs"]),
    ("ssrf-safe-fetch", [tool("fetch_url", "Fetch web URL", "url"), tool("check_destination", "Block SSRF DNS rebinding and internal network redirects", "url"), tool("crawl_site", "Crawl web site", "url")], ["check_destination", "fetch_url"], []),
    ("github-no-delete", [tool("list_issues", "Read GitHub issues", "repo"), tool("get_pull_request", "Read GitHub pull requests", "repo"), tool("add_review_comment", "Add GitHub review comment", "pr"), tool("delete_repository", "Delete GitHub repository", "repo")], [], ["delete_repository"]),
    ("twilio-sms-no-voice", [tool("send_sms", "Send Twilio SMS", "to"), tool("create_call", "Create Twilio voice call", "to")], [], ["create_call"]),
    ("speaker-diarization-gap", [tool("get_transcript", "Fetch podcast transcript", "url"), tool("download_audio", "Download podcast audio", "url")], [], []),
]


def words(value):
    return set(re.findall(r"[a-z0-9]+", value.lower())) - STOP


def cost(tools):
    # Tokenizer-independent approximation, not a provider bill or measured model tokens.
    # Compact UTF-8 JSON bytes / 4 is deliberately labelled "estimated" throughout.
    payload = json.dumps(tools, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return (len(payload.encode("utf-8")) + 3) // 4


def evaluate():
    corpus = json.loads(CORPUS.read_text(encoding="utf-8"))
    queries = {query["id"]: query for query in corpus["queries"]}
    assert len(queries) == 32
    rows = []
    for query_id, authored_tools, gold, excluded in CASES:
        assert query_id in queries
        query = queries[query_id]
        assert bool(gold) != query["expectedAbstain"]
        assert len(gold) == len(set(gold))
        assert not set(gold) & set(excluded)
        for size in ([len(authored_tools), 64] if query_id in {"github-workflow-automation", "postgres-dba-analysis"} else [len(authored_tools)]):
            tools = list(authored_tools)
            while len(tools) < size:
                index = len(tools)
                tools.append(tool(f"catalog_{index:02d}", f"Read catalog entry {index} metadata and status", "id", "filter"))
            names = {item["name"] for item in tools}
            assert set(gold + excluded) <= names
            # A deliberately simple task-description lexical proposal, capped at three.
            # It has no access to the gold labels when selecting.
            scores = {item["name"]: len(words(query["requirement"]) & words(item["name"] + " " + item["description"])) for item in tools}
            selected = [item for item in sorted(tools, key=lambda item: (-scores[item["name"]], item["name"])) if scores[item["name"]] > 0][:3]
            chosen = {item["name"] for item in selected}
            hits = len(chosen & set(gold))
            rows.append({"query": query_id, "catalog": size, "gold": gold, "selected": [item["name"] for item in selected],
                         "missing": sorted(set(gold) - chosen), "excluded_selected": sorted(chosen & set(excluded)),
                         "precision": round(hits / len(chosen), 3) if chosen else (1.0 if not gold else 0.0),
                         "recall": round(hits / len(gold), 3) if gold else (1.0 if not chosen else 0.0),
                         "complete": (set(gold) <= chosen and not chosen.intersection(excluded)) if gold else not chosen,
                         "full_estimated_tokens": cost(tools), "selected_estimated_tokens": cost(selected)})
    return rows


if __name__ == "__main__":
    rows = evaluate()
    positive = [row for row in rows if row["gold"]]
    no_match = [row for row in rows if not row["gold"]]
    for row in rows:
        print(json.dumps(row, sort_keys=True))
    print("SUMMARY", json.dumps({
        "cases": len(rows), "complete": sum(row["complete"] for row in rows),
        "complement_misses": sum(bool(row["missing"]) for row in rows),
        "excluded_selected": sum(bool(row["excluded_selected"]) for row in rows),
        "full_estimated_tokens": sum(row["full_estimated_tokens"] for row in rows),
        "selected_estimated_tokens": sum(row["selected_estimated_tokens"] for row in rows),
        "positive_macro_precision": round(sum(row["precision"] for row in positive) / len(positive), 3),
        "positive_macro_recall": round(sum(row["recall"] for row in positive) / len(positive), 3),
        "correct_no_match_abstentions": sum(not row["selected"] for row in no_match),
        "no_match_cases": len(no_match),
    }, sort_keys=True))
