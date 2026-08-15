# Frontend-specific agent guidance

These rules extend the root `AGENTS.md` for `web/`.

- Use Next.js App Router and TypeScript.
- Keep the product visually simple and developer-centric.
- The backend owns ranking and capability logic; never duplicate it client-side.
- Prefer server components; add `"use client"` only for interaction/state.
- Keep API types explicit and close to the client wrapper.
- Add accessible labels, visible focus behavior, loading states, errors, and useful empty states.
- Do not add a design-system dependency during V0.1 unless clearly justified.
