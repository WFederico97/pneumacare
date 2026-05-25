# AGENTS.md — pneumacare

## Stack

- **Java 17**, **Spring Boot 4.0.3**, Maven wrapper (`./mvnw` / `mvnw.cmd`)
- **Jackson 3.x** — package is `tools.jackson.databind`, NOT `com.fasterxml.jackson.databind`
- **Java package root**: `wfederico.pneumacare` (Maven groupId `com.pneumacare` is irrelevant for code)
- Architecture: **hexagonal monolith** (ports & adapters). `shared/` is the shared kernel. Add bounded contexts as sibling packages to `shared/`.

## Developer Commands

```bash
# Compile and unit-test only (no Docker required)
./mvnw test

# Full verify including integration tests (requires Docker for Testcontainers)
./mvnw verify -B

# Run locally without Docker (needs Postgres + Redis on localhost)
./mvnw spring-boot:run

# Docker Compose full stack
cp .env.example .env          # only needed once; edit passwords as needed
docker compose up
```

## Environment / Profiles

- Default profile: `dev`. Override with `SPRING_PROFILES_ACTIVE`.
- **`dev`**: Hibernate `ddl-auto: update`, Flyway disabled, **OAuth2 resource-server autoconfiguration excluded** — no JWT validation in dev.
- **`staging` / `prod`**: Flyway enabled, `ddl-auto: validate` (Flyway owns the schema).
- **Kafka is disabled by default** (`app.kafka.enabled: false`). Enable via `KAFKA_ENABLED=true` (or set it in `.env`). The `compose.yaml` forces `KAFKA_ENABLED=true` for the `app` service.

## Tests

- `./mvnw verify -B` runs both unit and integration tests. Integration tests spin up real containers via **Testcontainers** — Docker must be running.
- `PneumacareApplicationTests` (context-load smoke test) is annotated `@DisabledIfEnvironmentVariable(named = "CI", matches = "true")`. It is intentionally skipped in GitHub Actions, which instead relies on service containers (not Testcontainers).
- To write an integration test with all infrastructure: annotate with `@SpringBootTest` and `@Import(TestcontainersConfiguration.class)`. For Kafka add `properties = "app.kafka.enabled=true"`.

## Database / Flyway

- In `dev`, Hibernate manages DDL (`ddl-auto: update`). **Do not create Flyway scripts for dev-only changes.**
- Flyway migration scripts belong in `src/main/resources/db/migration/` (directory does not exist yet — create it when adding the first migration).
- Naming: `V{n}__{description}.sql` (Flyway standard).

## Known Incomplete / Commented-Out Features

- **`asciidoctor-maven-plugin`** is commented out in `pom.xml` (TODO: waiting for Spring Boot 4 compatibility). Spring REST Docs test support is present but HTML generation does not run in the build yet.
- **GraphQL schema** (`src/main/resources/graphql/schema.graphqls`) is a placeholder (`type Query { _placeholder: String }`).
- No Flyway migration scripts exist yet.

## Security

- `SecurityConfig` / `SecurityFilter` implement rate limiting via `RateLimitProperties` (`app.rate-limit.*`).
- OAuth2 JWT validation is live in staging/prod. In dev it is excluded via `spring.autoconfigure.exclude`.

## Observability

- OTLP endpoint defaults to `http://localhost:4318`. Set `OTEL_EXPORTER_OTLP_ENDPOINT` for non-default collectors.
- Grafana LGTM stack runs as the `grafana-lgtm` service in `compose.yaml`. Dashboard provisioning files are in `grafana/`.
- Prometheus metrics exposed at `/actuator/prometheus`.

## CI

- CI workflow (`.github/workflows/ci.yml`) triggers on push to `main`, `develop`, `feat/**`, and version tags.
- Pipeline: `./mvnw verify -B` → upload surefire reports → build (but not push) Docker image.
- Docker image pushed to `ghcr.io/wfederico97/pneumacare` only on `main` push or `v*.*.*` tag.
- Release tag (`v*.*.*`) also auto-opens a backport PR to `develop`.
- OpenCode agent available in PRs/issues via `/oc` or `/opencode` comment trigger (`.github/workflows/opencode.yml`).
