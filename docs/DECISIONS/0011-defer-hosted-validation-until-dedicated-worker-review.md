# ADR 0011: Report worker absence and defer hosted validation

## Status

Accepted for EVI-05. Production worker activation remains a separate EVI-13 decision.

## Context and options reviewed

The Vercel API stores validation jobs but cannot run the Docker based worker. Queue acceptance is not execution evidence. Three hosting paths were considered:

| Path | Security and operation | Cost decision |
| --- | --- | --- |
| Run Docker beside the API | Gives the web/API trust boundary a container-runtime endpoint and untrusted code lifecycle. Reject. | Shared compute does not offset that boundary crossing. |
| Dedicated hardened runtime host | Preserves ADR 0008's separate worker and existing ephemeral container controls. Requires host patching, restricted database credentials, monitoring, cleanup, incident response, and an ongoing host bill. | Candidate for EVI-13 only after a named owner approves a monthly cap and idle/peak budget. |
| Per-job managed sandbox | Could avoid an always-on host but needs a new adapter and proof that network, resources, cleanup, and result persistence match ADR 0008. | Evaluate only with a measured per-job and idle cost; no adapter exists today. |

## Decision

Do not activate a hosted worker in EVI-05. The API reports a recent heartbeat from the separate `queue` worker, with a 30-second freshness window. No heartbeat or a stale heartbeat is `UNAVAILABLE`; this is an observation, not a promise that Docker or a job will succeed. `QUEUED` means only that the immutable project snapshot was persisted. The API gives an operator action and retains the job lifecycle as the execution source of truth. `RUNNING` with a stale heartbeat is an uncertain outcome, not a failed or completed test.

EVI-13 may deploy one dedicated worker only after review of the host hardening checklist, database access, egress policy, cleanup/recovery, monitoring, and a priced monthly cost cap. The reviewer and operator must record the chosen host, region, monthly baseline and peak estimate, alert threshold, and a stop condition before production activation. Until then, hosted jobs can remain queued and product copy must say so.

## Consequences

The worker writes a database timestamp every ten seconds while the `queue` process is alive, including during long validations. The API performs a read-only lookup; it never starts containers or executes submitted code. Heartbeats do not prove execution readiness, and stale `RUNNING` jobs still need an operator recovery procedure. No untrusted workload is moved into the Vercel API process.
