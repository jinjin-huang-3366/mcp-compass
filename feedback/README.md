# Feedback intelligence workspace

`sources.json` configures the public sources and thresholds used by the local feedback research CLI. Generated raw
state belongs under ignored `.local/feedback/`; do not commit it. Sanitized aggregate reports may be deliberately
copied into `feedback/reports/` after checking that they contain no unnecessary personal data or credentials.

See [`docs/FEEDBACK_AGENT.md`](../docs/FEEDBACK_AGENT.md) for commands, approval rules, and extension guidance.

The [2026-10-25 six-month ecosystem review](reports/2026-10-25-six-month-market-analysis.md) is a curated,
source-linked feature-improvement analysis. It explains why the bounded collector's automatic cluster counts are
not a six-month market census or roadmap commitments.
