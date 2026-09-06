# Demo quality gate v1

## Exit thresholds

EXIT-05 uses the fixed `registry-relevance-v1` labels and recorded Registry snapshot. The checked-in
`RegistryRelevanceEvaluationTest` is the executable gate; labels are not generated from search output and the exit
task must not weaken them to pass.

| Metric | Required | Gate result |
| --- | ---: | ---: |
| Recall@100 | at least 95% | 51/53 (96.2%) |
| NDCG@10 | at least 0.80 | 0.8620 |
| Acceptable server in top three | at least 90% | 24/24 (100%) |
| Forbidden-result violations in top three | zero | 0 |
| Correct abstention when expected | at least 90% | 8/8 (100%) |

Run the fixed local gate with:

```bash
./mvnw -pl backend -Dtest=RegistryRelevanceEvaluationTest test
```

## Named production demos

The EXIT-05 task workflow also exercises the promoted production backend through authenticated Vercel access. It
sends the exact labelled requirements for these four canonical cases:

| Demo | Expected production behavior |
| --- | --- |
| `github-no-delete` | Preserve `github.repository.delete` and `github.branch.delete`; abstain with no matches. |
| `twilio-sms-no-voice` | Preserve `twilio.voice.call.create`; abstain rather than return a voice-capable Twilio server. |
| `postgres-read-only` | Preserve the four explicit write prohibitions and return a labelled read-only server in the top three, with no labelled write-capable result. |
| `web-docs-readonly` | Preserve publish/edit prohibitions and return a labelled read-only documentation server in the top three, with no labelled editing/publishing result. |

`.github/scripts/verify_production_demo_searches.py` validates the API responses against the reviewed REL-01 labels.
The workflow prints only a bounded summary and does not upload production response bodies as artifacts. The task
workflow run is the evidence that all four promoted-production checks passed for the pull request handoff.

## Production candidate

Backend commit `8a82f04d2837696798d5dfcd7968876900e5e55e` was staged, health/cron-auth checked, promoted, and health checked
again by [production deployment run 34035218646](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34035218646).
[production relevance run 34056877693](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34056877693)
then ingested 100 Registry pages and completed search-document and embedding coverage for all 3,934 corpus servers.
A post-run production query ranked `capital.hove/read-only-local-postgres-mcp-server` first for the canonical
`postgres-read-only` requirement while preserving all five parsed write prohibitions.
The linked EXIT-05 task workflow must still pass the fixed REL-01 metrics and all four authenticated searches against
that promoted backend before this acceptance record is merged and the plan item is marked complete.
