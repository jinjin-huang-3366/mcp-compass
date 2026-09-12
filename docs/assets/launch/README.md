# Launch screenshot provenance

These screenshots are repository documentation assets captured on 2026-09-12 from the deployed MCP Compass frontend
at `https://mcp-compass-jinjin-huang-3366s-projects.vercel.app/`.

The deployed frontend's browser search currently calls a protected immutable backend deployment. To avoid presenting
that broken browser path as verified, the capture rendered the production frontend while fulfilling its requests
with responses fetched directly from the stable production API alias at
`https://mcp-compass-api-jinjin-huang-3366s-projects.vercel.app/`. MKT-14 must correct and independently verify the
canonical public demo before launch.

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
