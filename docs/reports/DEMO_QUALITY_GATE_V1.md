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

Backend commit `52b94018e2f6159a44c21851cded6e767a408955` was staged, health/cron-auth checked, promoted, and health checked
again by [production deployment run 33964131472](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/33964131472).
The linked EXIT-05 task workflow must still pass the fixed REL-01 metrics and all four authenticated searches against
that promoted backend before this acceptance record is merged and the plan item is marked complete.
