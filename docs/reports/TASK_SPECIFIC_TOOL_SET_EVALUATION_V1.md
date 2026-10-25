# Task-specific tool-set comparison: EVI-08 decision

## Decision (2026-10-25)

**No-go for showing a selected minimal tool set or exclusions as product advice, and no-go for a runtime router.** A three-tool lexical proposal reduces estimated schema context sharply, but only 6/12 evaluated catalogs preserve the complete task or correctly abstain. It misses a complementary action in 2/12 and includes a labelled excluded action in 3/12. The source corpus has no observed `tools/list` schemas, so even a better result on this simulated inventory would not justify a user-facing claim.

Keep the current server-level recommendation and missing-capability explanation. The proposed UI must wait for observed or otherwise provenance-labelled tool inventories, independently reviewed task-step labels, and a separate evaluation of hard constraints. An excluded tool in a *display* would not enforce a permission boundary; only server-side authorization can do that.

## Method and evidence boundary

The [REL-01 corpus](../../backend/src/test/resources/fixtures/ranking/registry-relevance-v1.json) contains 32 labelled requirements and the [Registry snapshot](../../backend/src/test/resources/fixtures/ranking/registry-snapshot-2026-08-31.json) contains 28 publisher records. Neither contains observed tool schemas. This evaluation references ten unchanged REL-01 query IDs spanning GitHub, Twilio, PostgreSQL, web docs, and no-match cases. For each, `scripts/evaluate_tool_sets.py` authors a small *hypothetical* tool inventory and a task-step gold set. The gold set is independent of selection scoring but is authored by the same evaluator; there is no claim that these tools exist on the labelled servers. Two cases are repeated with 64-tool catalogs made by adding 60 generic catalog decoys. Those large catalogs test context scaling, not real-world retrieval distribution.

The proposal takes at most three tools with positive overlap between requirement words and tool name/description words, ordered by overlap and name. It does not use the gold set, tool arguments, forbidden constraints, or a router. A full catalog is the context-cost baseline. A case succeeds only if every gold task step is selected and no excluded tool is selected; no-match cases must select nothing. A missing gold action counts as a complementary-tool miss. Token cost is **estimated**, using compact UTF-8 JSON bytes divided by four and rounded up per catalog. It includes names, descriptions and input schemas, but is not a model-tokenizer count or a billing estimate. Exact encoding, schema length, prompt framing and tool-call overhead can change the savings.

## Results

| Measure | Result |
| --- | ---: |
| Distinct REL-01 requirements / evaluated catalogs | 10 / 12 |
| Complete task or correct abstention | 6/12 (50%) |
| Cases missing at least one complementary tool | 2/12 |
| Cases selecting an explicitly excluded tool | 3/12 |
| Mean precision / recall on nine positive catalogs | 0.852 / 0.926 |
| Correct abstention on three no-match or hard-gap catalogs | 0/3 |
| Full / selected estimated schema tokens | 13,555 / 2,082 (84.6% fewer) |
| Two 64-tool cases, full / selected estimate | 11,505 / 381 (96.7% fewer) |

The two complement misses are `github-workflow-automation` at 4 and 64 tools: the proposal selects `get_workflow_logs` ahead of `run_workflow`. It cannot carry out the requested workflow run despite choosing repository and release tools. `web-docs-readonly` selects `publish_docs`; `github-no-delete` selects `delete_repository`; `twilio-sms-no-voice` selects `create_call`. The no-match speaker-diarization case also incorrectly selects transcript/audio tools, so a tool-level abstention gate is needed beyond a three-tool cap. None of these outcomes proves actual server permissions because the inventories are hypothetical.

Concrete user-visible comparison: for “Manage GitHub repositories, releases, users, and automate workflows,” the 64-tool catalog costs an estimated 5,743 schema tokens. The three-tool proposal costs 181, but contains `list_repositories`, `get_workflow_logs`, and `manage_release`; it omits `run_workflow`. Showing that trio as a minimal sufficient set would mislead the developer about completing the task.

## Reproduce and next gate

From the repository root, run `python scripts/evaluate_tool_sets.py`. It validates the 32-query corpus link and prints every selection, missing and excluded action, cost, and aggregate result. No network, credentials, server execution, or mutation is involved. The test is a desk evaluation of a possible presentation, not a benchmark of the existing search service.

Before reconsidering a display, collect a bounded, versioned, provenance-labelled tool-schema sample across the same task types, including genuinely large catalogs. Have a second reviewer label necessary and forbidden task steps without seeing selected output. Measure complete-set rate, missing complements, false exclusions, abstention, and tokenizer-specific prompt cost at fixed budgets; keep hard constraint and permission evidence separate. A reasonable release gate is at least 95% complete sets, zero excluded tools in the reviewed hard-condition cases, and no implied enforcement claim. This small synthetic study cannot estimate those rates for production.
