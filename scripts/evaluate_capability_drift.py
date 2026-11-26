"""Offline EVI-09 evaluation with authored, EVI-04-shaped repeat observations."""

from dataclasses import dataclass, replace
from datetime import datetime, timedelta, timezone
from hashlib import sha256
import json


MAX_PAGES = 2
MAX_TOOLS = 6
FIRST_CHECK = datetime(2026, 11, 18, 12, tzinfo=timezone.utc)


@dataclass(frozen=True)
class Page:
    requested_cursor: str | None
    result: dict


@dataclass(frozen=True)
class Observation:
    checked_at: datetime
    registry_version: str | None
    server_version: str | None
    pages: tuple[Page, ...]
    state: str = "REACHABLE"
    # Registry name, endpoint, scope label, and negotiated protocol.
    context: tuple[str, str, str, str] = ("fixture/docs", "https://example.invalid/mcp", "anonymous", "2025-06-18")


def page(*tools, requested_cursor=None, next_cursor=None):
    result = {"tools": list(tools)}
    if next_cursor is not None:
        result["nextCursor"] = next_cursor
    return Page(requested_cursor, result)


FETCH = {
    "name": "fetch_doc",
    "description": "Read a document",
    "inputSchema": {
        "type": "object",
        "properties": {"path": {"type": "string"}, "format": {"type": "string"}},
        "required": ["path", "format"],
    },
}
LIST = {"name": "list_docs", "inputSchema": {"type": "object"}}
SEARCH = {"name": "search_docs", "inputSchema": {"type": "object"}}
BASE = Observation(FIRST_CHECK, "1.0", "4.0", (page(FETCH, LIST),))
REPEAT = replace(BASE, checked_at=FIRST_CHECK + timedelta(hours=24))


@dataclass(frozen=True)
class Case:
    name: str
    label: str
    repeat: Observation
    baseline: Observation = BASE


CASES = (
    Case("time-only", "stable", REPEAT),
    Case("tool-order", "stable", replace(REPEAT, pages=(page(LIST, FETCH),))),
    Case("schema-key-order", "stable", replace(REPEAT, pages=(page(
        {**FETCH, "inputSchema": dict(reversed(tuple(FETCH["inputSchema"].items())))}, LIST),))),
    Case("page-boundary", "stable", replace(REPEAT, pages=(
        page(FETCH, next_cursor="next"), page(LIST, requested_cursor="next")))),
    Case("description-copyedit", "stable", replace(REPEAT, pages=(page(
        {**FETCH, "description": "Read the selected document"}, LIST),))),
    Case("registry-version-only", "stable", replace(REPEAT, registry_version="1.1")),
    Case("server-version-only", "stable", replace(REPEAT, server_version="4.1")),
    Case("required-array-order", "stable", replace(REPEAT, pages=(page(
        {**FETCH, "inputSchema": {**FETCH["inputSchema"], "required": ["format", "path"]}}, LIST),))),
    Case("versioned-addition", "ordinary change", replace(REPEAT, pages=(page(FETCH, LIST, SEARCH),),
        registry_version="1.1", server_version="4.1")),
    Case("same-version-removal", "ordinary change", replace(REPEAT, pages=(page(FETCH),),
        checked_at=FIRST_CHECK + timedelta(hours=48))),
    Case("input-schema-change-missing-versions", "ordinary change", replace(REPEAT, pages=(page(
        {**FETCH, "inputSchema": {**FETCH["inputSchema"], "required": ["path"]}}, LIST),),
        registry_version=None, server_version=None), replace(BASE, registry_version=None, server_version=None)),
    Case("output-schema-addition", "ordinary change", replace(REPEAT, server_version="4.1", pages=(page(
        {**FETCH, "outputSchema": {"type": "object"}}, LIST),))),
    Case("auth-gated", "unknown", replace(REPEAT, state="AUTH_REQUIRED", pages=())),
    Case("missing-page", "unknown", replace(REPEAT, pages=(page(FETCH, next_cursor="next"),))),
    Case("page-cap", "unknown", replace(REPEAT, pages=(page(FETCH, next_cursor="next"),
        page(LIST, requested_cursor="next", next_cursor="third"), page(SEARCH, requested_cursor="third")))),
    Case("changed-context", "unknown", replace(REPEAT, context=(
        "fixture/docs", "https://example.invalid/mcp", "different-scope", "2025-06-18"))),
    Case("duplicate-tool", "unknown", replace(REPEAT, pages=(page(FETCH, FETCH),))),
    Case("missing-listing", "unknown", replace(REPEAT, pages=())),
)


def fingerprint(value, *, sort_keys):
    serialized = json.dumps(value, sort_keys=sort_keys, separators=(",", ":"), ensure_ascii=False)
    return sha256(serialized.encode("utf-8")).hexdigest()


def contracts(observation):
    if observation.state != "REACHABLE":
        raise ValueError(observation.state)
    if not observation.pages:
        raise ValueError("missing listing")
    if len(observation.pages) > MAX_PAGES:
        raise ValueError("page cap exceeded")
    expected_cursor = None
    seen_cursors = set()
    tools = {}
    for index, page in enumerate(observation.pages):
        if page.requested_cursor != expected_cursor or expected_cursor in seen_cursors:
            raise ValueError("broken or repeated cursor")
        seen_cursors.add(expected_cursor)
        for tool in page.result["tools"]:
            name = tool["name"]
            if name in tools:
                raise ValueError("duplicate tool name")
            tools[name] = {key: tool[key] for key in ("name", "inputSchema", "outputSchema") if key in tool}
        expected_cursor = page.result.get("nextCursor")
        if (index + 1 == len(observation.pages)) != (expected_cursor is None):
            raise ValueError("incomplete listing or pages after terminal cursor")
    if len(tools) > MAX_TOOLS:
        raise ValueError("tool cap exceeded")
    return [tools[name] for name in sorted(tools)]


def version_delta(before, after):
    if before is None or after is None:
        return "unknown"
    return "changed" if before != after else "same"


def compare(before, after):
    registry = version_delta(before.registry_version, after.registry_version)
    server = version_delta(before.server_version, after.server_version)
    evidence = {
        "checkedAt": [before.checked_at.isoformat(), after.checked_at.isoformat()],
        "hours": (after.checked_at - before.checked_at) // timedelta(hours=1),
        "registryVersion": [before.registry_version, after.registry_version, registry],
        "serverVersion": [before.server_version, after.server_version, server],
    }
    try:
        if before.context != after.context:
            raise ValueError("different endpoint, identity, scope or protocol")
        if after.checked_at <= before.checked_at:
            raise ValueError("observations out of order")
        old = fingerprint(contracts(before), sort_keys=True)
        new = fingerprint(contracts(after), sort_keys=True)
    except ValueError as error:
        return {**evidence, "unknown": str(error)}
    return {
        **evidence,
        "version-only": "changed" in (registry, server),
        "serialized listing": (fingerprint([page.result for page in before.pages], sort_keys=False)
                               != fingerprint([page.result for page in after.pages], sort_keys=False)),
        "contract fingerprint": old != new,
        "hashes": [old[:12], new[:12]],
    }


def print_results(cases):
    rows = [(case.name, case.label, compare(case.baseline, case.repeat)) for case in cases]
    for name, label, evidence in rows:
        print(f"{name} ({label}): {json.dumps(evidence)}")
    comparable = [(label, evidence) for _, label, evidence in rows if "unknown" not in evidence]
    print(f"\nComparable: {len(comparable)}/{len(rows)}")
    for rule in ("version-only", "serialized listing", "contract fingerprint"):
        for label in ("ordinary change", "stable"):
            group = [evidence[rule] for truth, evidence in comparable if truth == label]
            measure = "ordinary changes flagged" if label == "ordinary change" else "false alarms"
            print(f"{rule}, {measure}: {sum(group)}/{len(group)}")


if __name__ == "__main__":
    print_results(CASES)
