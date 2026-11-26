# Capability drift evaluation (EVI-09)

**No-go for user-facing alerts.** A fingerprint of declared tool contracts detected all four ordinary changes in a controlled replay and flagged one of eight stable pairs. Six more pairs had insufficient or incomparable evidence. EVI-04 has no repeat listing history to establish how often any of these outcomes occur in production. A catalog change can reflect a normal release or rollout. Its cause and runtime effects remain unknown without more evidence.

## Evidence and method

EVI-04 sends `initialize` and stores the latest connection state, check time, and reason. A changed Registry payload can clear the observation. The current fields cannot reconstruct older checks, the server's reported version, or a historical `tools/list` result. This evaluation reviewed that implementation and ran its existing local probe, source, and service tests. It had zero real repeat `tools/list` pairs. [Remote connection data model](../DATA_MODEL.md#remote-connection-observation) · [Probe implementation](../../backend/src/main/java/dev/mcpcompass/remote/HttpRemoteConnectionProbe.java)

The offline replay contains 18 authored baseline/repeat pairs with synthetic UTC timestamps 24 or 48 hours apart. Each listing uses fixture data from the same fictional Streamable HTTP server unless the case changes its observation context. The replay accepts at most two cursor-linked pages and six tools per observation. Six pairs are excluded from all three comparisons because a listing is missing, auth-gated, incomplete, over the page cap, ambiguous due to duplicate tool names, or from a different scope. A successful `initialize` alone gives no listing fingerprint. The replay makes no network requests and uses no credentials. [Evaluator and fixture cases](../../scripts/evaluate_capability_drift.py) · [MCP tool listing](https://modelcontextprotocol.io/specification/2025-06-18/server/tools) · [MCP pagination](https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/pagination)

The cases were labelled before comparing rules by the same evaluator who authored them. An ordinary change is an added or removed tool or a changed input or output contract. A stable pair can still contain a version bump, representation changes, or an editorial copyedit. Each rule uses the same 12 comparable pairs.

| Rule | Ordinary changes flagged | False alarms on stable pairs |
| --- | ---: | ---: |
| Either known version changed (Registry version or MCP `serverInfo.version`) | 2/4 (50%) | 2/8 (25%) |
| Serialized listing result pages changed, including cursor, order, and descriptions | 4/4 (100%) | 5/8 (62.5%) |
| Canonical contract fingerprint changed | 4/4 (100%) | 1/8 (12.5%) |

The contract fingerprint hashes complete catalogs with SHA-256. It sorts tools by name and JSON object members by key, and includes each name, `inputSchema`, and optional `outputSchema`. It keeps schema array order. Reordering the two values in `required` is the remaining false alarm because the same fields are required in both orders. The fingerprint omits top-level descriptions, titles, and annotations, so it cannot detect meaningful changes confined to those fields or to implementation behavior. Identical hashes only establish equality of the chosen fields in the sampled listings. Observation time shows when evidence was collected and cannot date the underlying change. Unknown versions are reported explicitly and do not prevent comparing complete listings of the same endpoint, scope, and negotiated protocol.

## Concrete example and reproduction

In `same-version-removal`, the fixture checked on November 18 at 12:00 UTC contains `fetch_doc` and `list_docs`. Forty-eight hours later only `fetch_doc` appears. Registry version `1.0` and server version `4.0` are unchanged. The contract hash prefix changes from `7143f69f8c4f` to `2db337a7e552`. The version-only rule misses the removal. In `missing-page`, a similar first page contains only `fetch_doc`, but it advertises a next cursor whose response is absent, so the result is unknown.

1. From the repository root, run `python scripts/evaluate_capability_drift.py`. Expect `Comparable: 12/18` and the six counts in the table. Compare `same-version-removal`, `required-array-order`, `page-boundary`, and `missing-page` against the outcomes above. Repeat the command to verify identical hashes and counts.
2. Run `./mvnw -pl backend '-Dtest=HttpRemoteConnectionProbeTest,RemoteConnectionCheckServiceTest,RegistryRemoteConnectionSourceTest' test` (`.\mvnw.cmd` on Windows, or `mvn` if installed). Expect four existing EVI-04 tests to pass. They validate current connection-check behavior with loopback or mocked fixtures. Replaying the listing pairs requires no running app or database.
3. Run `git diff --name-only origin/main...HEAD` on the committed branch. Expect this report and the offline script. Live repeat collection and product alert testing were outside the experiment because EVI-04 has no listing history and the branch adds no alert implementation.

## Before reconsidering alerts

Review a separate opt-in, bounded metadata collection proposal with a time and request budget, complete pagination within agreed limits, an explicit comparison context, and consistent treatment of auth gates, opt-outs, throttling, and failures as unknown. Record version and observation time alongside each comparable listing. Repeat observations after a candidate change to measure transient differences, and have a second reviewer check ordinary rollout, representation churn, and unexplained changes before setting a user-facing threshold. Track coverage and the false-alarm denominator separately. The fixture results do not justify setting a production alert threshold.
