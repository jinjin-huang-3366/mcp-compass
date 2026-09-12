# Launch screenshot provenance

These screenshots are repository documentation assets captured on 2026-09-12 from the deployed MCP Compass frontend
bundle now linked through the working public alias at `https://mcp-compass-iota.vercel.app/`.

The two frontend aliases checked during capture served the same JavaScript bundle targeting
`https://mcp-compass-api.vercel.app/`. The production API accepted an `mt5` preflight and search from the `iota`
origin with HTTP 200 and a matching `Access-Control-Allow-Origin` header, while it rejected the initially selected
project alias with HTTP 403. The screenshots render that production frontend bundle with responses from the stable
production API. MKT-14 must independently re-verify the canonical public demo before launch.

| Asset | Viewport | Demonstrated state | Accessibility/size choice |
| --- | --- | --- | --- |
| `search-ranking.png` | 1440 × 1200 desktop | Read-only PostgreSQL intent, first ranked match, and score explanation | README alt text names the important state; later results were hidden to keep the PNG to a representative 73 KB. |
| `search-abstention-mobile.png` | 390 × 844 mobile | GitHub delete prohibitions and no-strong-match explanation | The element capture tests the narrow layout; the 46 KB still preserves readable text. |
| `contract-review.png` | 1440 × 1100 desktop | Two OpenAPI-derived tools with editable contract fields and risk labels | The 29 KB still shows both read-only and mutating review states. |

The production API inputs were the canonical `postgres-read-only` and `github-no-delete` requirements from
`backend/src/test/resources/fixtures/ranking/registry-relevance-v1.json`, plus an in-memory two-operation OpenAPI
document titled `Launch Pets API`. No credentials, cookies, private specifications, or response artifacts are stored.

No animated GIF was produced because motion did not add information beyond these three states. All public-facing
README images have descriptive alt text and no essential instruction exists only inside an image.
