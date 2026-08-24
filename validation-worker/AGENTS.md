# Validation-worker agent guidance

- This module is the only production boundary allowed to materialize or launch generated or third-party MCP code.
- Never execute workload commands with a host shell or JVM process. Pass commands as container arguments.
- Do not expose the Docker socket, host credentials, or host paths other than the per-job workspace to workload containers.
- Keep container lifecycle handling deterministic and cleanup best-effort on every outcome.
- Protocol validation belongs to VAL-03. Full resource/network policy and reporting belong to VAL-04/VAL-05.
