# V0.1 top-three acceptance review

## Scope

This review covers ten requirements manually selected from the versioned `lexical-relevance-v1` fixture. The
selection spans source control, communication, databases, issue tracking, object storage, orchestration,
document storage, observability, constrained messaging, and email. It deliberately includes the PostgreSQL
read-only and Twilio SMS-only cases that expose known lexical top-one weaknesses.

For this V0.1 exit criterion, a top three is sensible when it contains at least one server that the fixture's
human-reviewed labels identify as acceptable for the requirement. This matches the product success measure of
a clearly acceptable top-three match. It does not claim that every returned candidate is acceptable or that
the first result always satisfies forbidden capabilities and constraints.

## Results

| Requirement | Acceptable server | Rank | Observed top three |
| --- | --- | ---: | --- |
| GitHub issues and pull requests | `github` | 1 | `github`, `gitlab`, `linear` |
| Slack channel messaging | `slack` | 1 | `slack`, `discord`, `gmail` |
| PostgreSQL read-only queries | `postgres-readonly` | 2 | `postgres-admin`, `postgres-readonly`, `sqlite` |
| Jira Cloud issues | `jira` | 1 | `jira`, `github`, `linear` |
| Amazon S3 reader | `s3` | 1 | `s3`, `filesystem`, `docker` |
| Kubernetes pod observer | `kubernetes` | 1 | `kubernetes`, `filesystem`, `sentry` |
| Google Drive reader | `google-drive` | 1 | `google-drive`, `filesystem`, `notion` |
| Sentry release reader | `sentry` | 1 | `sentry`, `kubernetes`, `datadog` |
| Twilio SMS only | `twilio` | 2 | `twilio-communications`, `twilio`, `gmail` |
| Gmail mail assistant | `gmail` | 1 | `gmail`, `slack`, `google-drive` |

Acceptance result: **10/10 requirements have a labelled acceptable server in the top three.**

## Reproduce

From the repository root:

```bash
./mvnw -pl backend -Dtest=RankingEvaluationTest test
```

`tenManuallySelectedRequirementsHaveAnAcceptableTopThreeResult` fixes the ten query IDs, checks the selection
has exactly ten distinct entries, requires an acceptable result at rank 1 through 3, and requires the leading
score to meet the fixture's strong-match threshold. The test prints each observed top three for review.

The test is deterministic and uses the bounded synthetic server records from the fixture; it does not query the
public Registry or claim database-backed retrieval coverage. The lexical baseline report separately records the
known bad-candidate and top-one limitations that remain after this exit check.
