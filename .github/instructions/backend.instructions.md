---
applyTo: "backend/**/*.java,backend/**/*.yml,backend/**/*.sql,backend/pom.xml"
---

Use Java 21 idioms and Spring Boot conventions. Prefer records for immutable DTOs. Use constructor injection. Keep controllers thin and ranking/business logic independently unit-testable. Never expose JPA entities directly from REST endpoints. Use Flyway for schema changes. External Registry/GitHub/LLM calls must be behind clients/interfaces and have timeouts. Do not add untrusted process execution to the backend JVM.
