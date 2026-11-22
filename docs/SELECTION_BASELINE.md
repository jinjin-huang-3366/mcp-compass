# Selection measurement baseline

## Deployed evidence

Read-only check on 2026-11-22 at 16:32 UTC. The [production frontend](https://mcp-compass-iota.vercel.app/)
and its [Vercel analytics script](https://mcp-compass-iota.vercel.app/_vercel/insights/script.js) both returned
HTTP 200. The homepage's first-party JavaScript loaded the Vercel analytics SDK. The latest successful
[production deployment workflow](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709693340)
promoted and verified both projects at commit `9ca1212a902639dd279cf90e7628974314135f6d` on 2026-09-12.
That commit includes page-view analytics and has no selection event collector or choice question. The earlier
[MKT-11 deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34166221323) records the
referral analytics rollout.

The production analytics dashboard and visit totals were unavailable during the check. Record historical search,
detail, source and choice counts as unavailable and start the action baseline after rollout. An HTTP response
from the script verifies availability, while dashboard reporting depends on successful delivery, quota and
browser blocking. The existing
[Vercel Hobby analytics plan](https://vercel.com/docs/analytics/limits-and-pricing) supports referrer
reporting and excludes custom events and UTM reporting, so action totals use the existing PostgreSQL database.

## Events introduced in this change

| Event | What gets counted |
| --- | --- |
| `SEARCH_SUBMITTED` | A non-empty requirement submitted through **Find MCP** in the web UI. |
| `SEARCH_WITH_MATCHES` | The first-page response to a submitted search contains at least one match. |
| `SEARCH_WITHOUT_MATCHES` | The first-page response to a submitted search contains zero matches, including abstentions. |
| `SEARCH_FAILED` | Fetching the first-page response to a submitted search failed. |
| `DETAIL_OPENED` | Activation of **View MCP details** from a search result. |
| `SOURCE_OPENED` | Activation of **Source repository** on a result or server detail. |
| `CHOICE_CHANGED`, `CHOICE_UNCHANGED`, `CHOICE_PENDING` | An optional answer of **Yes**, **No**, or **I haven't decided** to the results-page question about changing which server the developer chose or ruled out. |

Page reloads, shared search links and pagination provide results without counting a form submission. Cached
first-page responses to explicit submissions count. Detail and source activations indicate intent to open the
destination. Browser context menus, direct detail visits, completed installations, and CLI or direct API
searches are outside the action counts. Feedback is self-reported and may include decisions driven by abstention.
Use consented follow-up conversations under VLD-02 for repeat-use evidence.

The selection endpoint accepts one fixed event name as a JSON string. Browser requests omit cookies and the
referrer, and carry no requirement, credentials, URL, candidate ID, campaign data, fingerprint or user/session
identifier. The database stores only a UTC day, event name and count. The Vercel page-view integration strips
query strings and fragments from the reported URL, groups detail pages, and uses the initial external referrer.
The application still supports shareable search URLs, which contain the requirement and can appear in browser
history or hosting request logs. Those URLs and logs are outside this measurement.

## Read and compare the totals

After deploying the backend and frontend, verify the selection endpoint and one end-to-end action journey in a
controlled environment before using the counts. Record the deployed commit and first confirmed collection time
in UTC. Read daily totals with an operator's existing database access. For example, on the local Compose
database with its default name and user:

```sh
docker compose exec -T db psql -U mcp_compass -d mcp_compass -c 'SELECT event_date, event_name, total FROM selection_event_daily ORDER BY event_date, event_name;'
```

Compare matched, empty and failed responses with submitted searches across complete UTC days. Report detail and
source actions as counts because one search can lead to several opens. Record all three feedback counts and the
number of voluntary responses. Lost requests, duplicated actions, midnight rollover, and untrusted callers to the
public write endpoint can affect totals. Treat a missing event on a verified collection day as no recorded events.
Publish no rates for unverified collection periods and do not use these counts as unique-person or conversion
counts.

## Campaign attribution

Keep a campaign log with channel, publication permalink, publish time in UTC and observation window. In Vercel
Analytics, record the relevant initial-referrer domain's counts for the first 24 hours and first seven days, with
matching pre-publication windows. Record the dashboard metric and dates actually shown, including partial windows.
Report search, detail, source and choice totals for the same UTC days as a separate aggregate series. A referrer
domain can include several posts and unrelated traffic. Missing referrers, shared links and simultaneous campaigns
can obscure attribution. This measures referral activity by domain and timing. Per-post attribution and
referrer-to-action conversion need additional evidence and cannot be inferred by joining these totals.
