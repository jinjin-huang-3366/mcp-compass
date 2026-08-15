# Architecture summary

- UI: Next.js/TypeScript.
- API/backend: Java 21/Spring Boot modular monolith.
- Persistence: PostgreSQL; pgvector available for later semantic retrieval.
- Registry ingestion: scheduled/background path into local storage.
- Search request path: local DB -> candidate retrieval -> ranking -> explanation.
- Future generator: source discovery -> tool contract -> code -> compile/test -> sandbox validation.
- Future sandbox: isolated worker/container, never the main backend JVM.
