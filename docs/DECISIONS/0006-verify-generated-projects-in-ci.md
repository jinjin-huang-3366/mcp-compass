# ADR 0006: Verify generated TypeScript projects in CI

## Status

Accepted.

## Context

The TypeScript generator returns an in-memory project manifest assembled from an approved contract and an
application-owned runtime pack. Static Java assertions protect the manifest shape, but they cannot prove that the
published TypeScript compiles or that its request-mapping behavior works with the locked dependency versions.

Compiling inside the production backend would cross the boundary between generation and sandboxed execution. It
would also add npm, network, process, and temporary-filesystem dependencies to an API request path. Full protocol
and containment checks belong to the later sandbox-validation milestone.

## Decision

The versioned runtime pack includes `package-lock.json` and application-owned Node unit tests. The tests mock
`fetch` and cover path encoding, query parameters, request bodies, authentication headers, and missing path values.

An opt-in Java test generates a representative project through the same `TypeScriptMcpProjectGenerator` used by the
API, validates and materializes every manifest path under an isolated Maven build directory, runs
`npm ci --ignore-scripts`, and runs `npm test`. Both repository CI workflows enable this test after installing
Node.js 22. Normal production code does not expose process execution and does not compile or run a generated project.

## Consequences

Every pull request proves that the exact generated manifest installs from its lockfile, compiles in strict TypeScript
mode, and passes its network-free unit tests. Dependency changes are explicit lockfile diffs. Local backend tests
remain fast and do not require Node unless the developer opts into the generated-project check.

This check does not claim MCP protocol correctness, upstream API compatibility, or safe runtime containment. Those
remain responsibilities of the isolated validation milestone.
