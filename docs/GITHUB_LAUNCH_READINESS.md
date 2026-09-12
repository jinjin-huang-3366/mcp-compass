# GitHub launch readiness

This checklist is the source of truth for preparing the public GitHub surface before MCP Compass is promoted through
external channels. Complete the linked `PLANS.md` tasks in order; do not treat this document itself as evidence that
the launch work is done.

## Audit snapshot

Checked on 2026-09-12 against the repository and its GitHub metadata before MKT-13:

- the README explains the architecture and local setup, but it does not lead with a public demo call to action or
  show the product;
- no launch screenshots or demo GIF are committed;
- no `CONTRIBUTING.md` or `CHANGELOG.md` exists;
- the existing agent-task issue template is useful for planned engineering work, but there are no public bug report
  or feature request forms;
- the GitHub repository has no Topics or homepage URL, Discussions is disabled, and no tagged release exists;
- the initially selected frontend alias loads the same deployed bundle as `https://mcp-compass-iota.vercel.app/`, but
  the production API CORS policy rejects the former origin and allows the `iota` origin.

Repository settings and release state can change independently of the code. Recheck them during MKT-14 and MKT-15
and record dated evidence rather than relying on this snapshot.

## MKT-13 — repository launch surface

- Rewrite the README for a first-time agent/MCP developer: problem, requirement-to-ranked-result workflow, reuse-first
  generation boundary, current limitations, public demo, quick start, and contribution path.
- Capture representative production screenshots for search results, ranking explanations or abstention, and
  contract review. Store optimized assets in a documented repository location and provide useful alt text.
- Add a short optimized GIF only when it explains an interaction better than the still images. Do not make an
  autoplaying GIF the only demonstration or the only source of important text.
- Add `CONTRIBUTING.md` with setup, validation, issue/PR expectations, security-report routing, and the project scope.
- Add public YAML issue forms for reproducible bug reports and focused feature requests while retaining the existing
  agent development template. Route security reports away from public issues and add issue-template configuration
  for the intended support links.
- Add a curated `CHANGELOG.md` with an Unreleased section and the evidence-backed features and material limitations
  intended for the first public release. Do not copy internal milestone completion logs into the changelog.
- Check every README, contribution, issue-form, changelog, screenshot, demo, documentation, and security link from a
  clean checkout.

Exit evidence: the MKT-13 pull request includes the files and images, desktop/mobile rendering checks, link-check
results, and any asset size or accessibility trade-offs.

MKT-13 evidence captured on 2026-09-12:

- `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, public YAML bug/feature forms, and issue-template configuration were
  added without changing GitHub repository settings;
- three optimized still PNGs cover desktop ranking/explanation, mobile abstention, and contract review; their
  provenance, viewport, size, and alt-text choices are recorded in `docs/assets/launch/README.md`; a GIF was omitted
  because motion added no clarity;
- both frontend aliases served the same bundle targeting `https://mcp-compass-api.vercel.app`; an `mt5` preflight and
  search from `https://mcp-compass-iota.vercel.app` returned HTTP 200 with the matching CORS origin, while the
  initially selected project alias was rejected with HTTP 403. The README now uses the verified `iota` URL;
- final link, YAML, desktop/mobile render, asset-size, and repository validation results are recorded in the linked
  MKT-13 pull request rather than asserted by this checklist alone.

## MKT-14 — GitHub metadata and community settings

- Verify a stable public demo URL. Put the same canonical URL in the README call to action and GitHub repository
  homepage; do not advertise an unverified custom domain.
- Add a small, search-oriented Topic set after checking GitHub's accepted topic names. Suggested intent, not fixed
  spelling: `mcp`, `model-context-protocol`, `ai-agents`, `developer-tools`, `mcp-server`, `mcp-registry`, and
  `spring-boot` or `nextjs` where useful.
- Enable Discussions only when a named repository owner will moderate it and the launch categories and links from
  `CONTRIBUTING.md` are ready. Start with bounded categories such as Announcements, Q&A, Ideas, and Show and tell;
  otherwise leave Discussions disabled and use issue forms until ownership exists.
- Record the final homepage URL, Topics, Discussions state/categories, operator, and verification date here or in a
  linked launch evidence record.

Exit evidence: the MKT-14 task records the GitHub URLs and settings observed after mutation. A source change alone
does not prove that repository settings were applied.

MKT-14 evidence captured on 2026-09-12:

- canonical demo/homepage: `https://mcp-compass-iota.vercel.app/`; the frontend returned HTTP 200, the production
  API accepted its CORS preflight with HTTP 200 and `Access-Control-Allow-Origin` set to that origin, and an `mt5`
  search returned HTTP 200 with a strong match;
- repository: `https://github.com/jinjin-huang-3366/mcp-compass`; homepage set to the canonical demo and Topics set
  to `ai-agents`, `developer-tools`, `mcp`, `mcp-registry`, `mcp-server`, `model-context-protocol`, `nextjs`, and
  `spring-boot`; GitHub's repository API accepted and returned the complete lowercase set;
- Discussions: enabled at `https://github.com/jinjin-huang-3366/mcp-compass/discussions` with GitHub's bounded launch
  categories [Announcements](https://github.com/jinjin-huang-3366/mcp-compass/discussions/categories/announcements),
  [General](https://github.com/jinjin-huang-3366/mcp-compass/discussions/categories/general),
  [Ideas](https://github.com/jinjin-huang-3366/mcp-compass/discussions/categories/ideas),
  [Polls](https://github.com/jinjin-huang-3366/mcp-compass/discussions/categories/polls),
  [Q&A](https://github.com/jinjin-huang-3366/mcp-compass/discussions/categories/q-a), and
  [Show and tell](https://github.com/jinjin-huang-3366/mcp-compass/discussions/categories/show-and-tell). Their intended
  uses and issue-routing boundary are documented in `CONTRIBUTING.md`;
- moderation owner/operator: repository owner
  [`@jinjin-huang-3366`](https://github.com/jinjin-huang-3366), whose admin permission was verified before enabling
  Discussions;
- verification used public HTTP responses and authenticated GitHub API reads after mutation. Visual signed-out
  browser verification was not performed because the local in-app browser runtime failed to initialize; it remains
  part of MKT-15's release-and-demo verification gate.

## MKT-15 — tagged GitHub release

- Start only after MKT-13 is merged and MKT-14 settings are verified.
- Select the public version deliberately; do not infer it from the internal V0.7 planning milestone. Confirm that the
  target `main` commit has successful baseline CI and is the exact commit deployed to the public demo.
- Update the dated changelog section and prepare concise release notes with the demo link, screenshots, major user
  workflows, measured claims, known limitations, contributor link, and comparison link.
- Create an immutable version tag on that verified commit and publish a non-draft, non-prerelease GitHub Release only
  when those labels are accurate. Do not retarget or force-update a published version tag.
- Verify the tag target, release page, downloadable source archives, demo URL, screenshots, and documentation links in
  a signed-out browser session. Record the release URL, tag, source SHA, CI run, deployment run, and verification date.

Exit evidence: the GitHub Release and demo are publicly reachable, the tag resolves to the recorded verified SHA,
and the evidence record contains no credentials or private operational data.

MKT-15 evidence captured on 2026-09-12:

- public version: `v0.1.0`, selected as the first early-stage public SemVer release independently of internal V0.7
  planning chronology;
- immutable tag/source SHA: `v0.1.0` at `9ca1212a902639dd279cf90e7628974314135f6d`;
- exact-source CI: [run 34709603842](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709603842)
  passed backend, web, CLI, and automation jobs;
- exact-source production deployment: [run 34709693340](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709693340)
  staged, smoke-tested, promoted, and rechecked both backend and frontend;
- release: [MCP Compass v0.1.0](https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0), published
  as a non-draft, non-prerelease release with evidence-backed notes from `CHANGELOG.md`;
- signed-out verification covered the release page, tag target, ZIP and tarball source archives, canonical demo,
  three version-pinned screenshots, contributor guide, evaluation report, and commit-history link. The canonical demo
  returned HTTP 200, and an `mt5` production search returned HTTP 200 with 40 matches and `strongMatch=true`.

## Channel launch gate

MKT-05, MKT-07, MKT-08, MKT-09, and MKT-10 remain behind both the exact-copy approval gate in
[`MARKETING.md`](MARKETING.md) and MKT-15. Final channel packages must use the verified canonical demo and release
URLs. If either URL changes, revise the package and obtain fresh approval before publishing.
