#!/usr/bin/env python3
"""Validate the four fixed REL-01 demo responses from the production API."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class DemoExpectation:
    strong_match: bool
    required_forbidden_capabilities: frozenset[str]
    acceptable_server_ids: frozenset[str] = frozenset()
    forbidden_server_ids: frozenset[str] = frozenset()


EXPECTATIONS = {
    "github-no-delete": DemoExpectation(
        strong_match=False,
        required_forbidden_capabilities=frozenset(
            {"github.repository.delete", "github.branch.delete"}
        ),
        forbidden_server_ids=frozenset(
            {"io.github.github/github-mcp-server", "com.mcparmory/github"}
        ),
    ),
    "twilio-sms-no-voice": DemoExpectation(
        strong_match=False,
        required_forbidden_capabilities=frozenset({"twilio.voice.call.create"}),
        forbidden_server_ids=frozenset(
            {
                "io.github.codespar/mcp-twilio",
                "io.github.mrfentmen/twilio-mcp-server",
                "io.github.pipeworx-io/twilio",
            }
        ),
    ),
    "postgres-read-only": DemoExpectation(
        strong_match=True,
        required_forbidden_capabilities=frozenset(
            {
                "postgres.row.insert",
                "postgres.row.update",
                "postgres.row.delete",
                "postgres.schema.write",
            }
        ),
        acceptable_server_ids=frozenset(
            {
                "io.github.Eszetael/postgres-mcp-hardened",
                "capital.hove/read-only-local-postgres-mcp-server",
                "io.github.arifulislamat/database-mcp-postgres",
                "io.github.infoinlet-marketplace/mcp-postgres-nl-query",
            }
        ),
        forbidden_server_ids=frozenset(
            {"io.github.itunified-io/postgres", "io.github.jpka/sw-postgres-mcp"}
        ),
    ),
    "web-docs-readonly": DemoExpectation(
        strong_match=True,
        required_forbidden_capabilities=frozenset(
            {"document.publish", "document.edit"}
        ),
        acceptable_server_ids=frozenset(
            {"com.shipshapedata/shipshape-data-docs", "io.github.ACTA-Team/docs-mcp"}
        ),
        forbidden_server_ids=frozenset(
            {"ai.auteng/docs", "io.github.A1-x-Tech/mcp-google-docs"}
        ),
    ),
}


def validate(demo_id: str, payload: dict) -> dict:
    expectation = EXPECTATIONS[demo_id]
    actual_strong_match = payload.get("strongMatch")
    if actual_strong_match is not expectation.strong_match:
        raise ValueError(
            f"{demo_id}: strongMatch was {actual_strong_match!r}, "
            f"expected {expectation.strong_match!r}"
        )

    intent = payload.get("parsedIntent")
    if not isinstance(intent, dict):
        raise ValueError(f"{demo_id}: parsedIntent must be an object")
    actual_forbidden = set(intent.get("forbiddenCapabilities") or [])
    missing_forbidden = expectation.required_forbidden_capabilities - actual_forbidden
    if missing_forbidden:
        raise ValueError(
            f"{demo_id}: missing forbidden capabilities {sorted(missing_forbidden)!r}"
        )

    matches = payload.get("matches")
    if not isinstance(matches, list):
        raise ValueError(f"{demo_id}: matches must be an array")
    top_three = [match.get("registryName") for match in matches[:3]]
    top_three_ids = {server_id for server_id in top_three if isinstance(server_id, str)}
    forbidden_results = expectation.forbidden_server_ids & top_three_ids
    if forbidden_results:
        raise ValueError(
            f"{demo_id}: forbidden results appeared in the top three: "
            f"{sorted(forbidden_results)!r}"
        )

    if expectation.strong_match:
        if not expectation.acceptable_server_ids & top_three_ids:
            raise ValueError(
                f"{demo_id}: no labelled acceptable server appeared in the top three; "
                f"received {top_three!r}"
            )
    else:
        if matches:
            raise ValueError(f"{demo_id}: abstention response still returned matches")
        reasons = payload.get("abstentionReasons")
        if not isinstance(reasons, list) or not reasons:
            raise ValueError(f"{demo_id}: abstention reasons must be non-empty")

    return {
        "demo": demo_id,
        "strongMatch": actual_strong_match,
        "topThree": top_three,
        "forbiddenCapabilities": sorted(actual_forbidden),
    }


def parse_response(value: str) -> tuple[str, Path]:
    demo_id, separator, path = value.partition("=")
    if not separator or demo_id not in EXPECTATIONS or not path:
        raise argparse.ArgumentTypeError(
            "responses must use a known demo ID and path: <demo-id>=<file>"
        )
    return demo_id, Path(path)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--response", action="append", required=True, type=parse_response,
        metavar="DEMO_ID=FILE",
    )
    args = parser.parse_args()

    response_paths = dict(args.response)
    if set(response_paths) != set(EXPECTATIONS):
        missing = sorted(set(EXPECTATIONS) - set(response_paths))
        unexpected = sorted(set(response_paths) - set(EXPECTATIONS))
        parser.error(f"expected all four demos; missing={missing}, unexpected={unexpected}")

    summaries = []
    for demo_id in EXPECTATIONS:
        with response_paths[demo_id].open(encoding="utf-8") as response_file:
            payload = json.load(response_file)
        if not isinstance(payload, dict):
            raise ValueError(f"{demo_id}: response must be a JSON object")
        summaries.append(validate(demo_id, payload))

    print(json.dumps(summaries, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
