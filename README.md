# Pneumacare

> Clinical decision-support backend for ICU respiratory physiotherapy.
> Spring Boot 4.0.3 · Java 17 · Maven · Hexagonal monolith · PostgreSQL · Redis · Kafka · Grafana LGTM

[![Build](https://github.com/wfederico97/pneumacare/actions/workflows/build.yml/badge.svg)](https://github.com/wfederico97/pneumacare/actions/workflows/build.yml)
[![CI](https://github.com/wfederico97/pneumacare/actions/workflows/ci.yml/badge.svg)](https://github.com/wfederico97/pneumacare/actions/workflows/ci.yml)
[![SAST](https://github.com/wfederico97/pneumacare/actions/workflows/sast.yml/badge.svg)](https://github.com/wfederico97/pneumacare/actions/workflows/sast.yml)

---

## Table of Contents

- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Shared Kernel](#shared-kernel)
  - [config](#config)
  - [constants](#constants)
  - [data](#data)
  - [event](#event)
  - [exception](#exception)
  - [security](#security)
  - [web](#web)
- [Event Publishing](#event-publishing)
- [Configuration](#configuration)
  - [application.yml](#applicationyml)
  - [Profile Overrides](#profile-overrides)
  - [Environment Variables](#environment-variables)
- [Docker Compose Services](#docker-compose-services)
- [Dockerfile](#dockerfile)
- [Security](#security-1)
- [Observability](#observability)
- [GraphQL](#graphql)
- [Testing](#testing)
- [CI/CD](#cicd)
  - [build.yml — Build pipeline](#buildyml--build-pipeline)
  - [ci.yml — Full integration pipeline](#ciyml--full-integration-pipeline)
  - [sast.yml — Static analysis](#sastyml--static-analysis)
  - [GitHub Secrets](#github-secrets)
- [Running](#running)
- [Releases and Docker Image](#releases-and-docker-image)

---

## Architecture

Pneumacare is a **hexagonal monolith** (ports and adapters). Each bounded context will live in its own top-level package and follow this internal layout:

```
<context>/
  application/
    command/        Command objects (write-side input)
    port/
      in/           Inbound ports (use-case interfaces)
      out/          Outbound ports (persistence, messaging contracts)
    service/        Use-case orchestration; implements inbound ports
  domain/
    event/          Domain events
    exception/      Domain exceptions
    model/          Aggregates and value objects (JPA annotations allowed)
  infra/
    in/             Inbound adapters (REST controllers, GraphQL resolvers, DTOs)
    out/            Outbound adapters (JPA repositories, persistence adapters)
```

**Dependency rule**: `infra → application → domain`. The `shared/` package is the cross-cutting kernel available to all layers.

> **Status**: The real domain (ICU respiratory physiotherapy) is being designed. The scaffold is fully wired and compiles cleanly. No bounded contexts exist yet.

---

## Project Structure

```
src/main/java/wfederico/pneumacare/
│
├── PneumacareApplication.java              Entry point. Excludes DatadogMetricsExportAutoConfiguration.
│
└── shared/                                 Cross-cutting kernel — shared by all bounded contexts.
    ├── config/
    │   ├── JpaAuditingConfig.java          @EnableJpaAuditing bean.
    │   ├── KafkaConfig.java                Kafka listener factory with DLT error recovery.
    │   ├── ModelMapperConfig.java          Singleton ModelMapper bean.
    │   ├── ObservabilityConfig.java        ObservedAspect bean for @Observed AOP support.
    │   ├── OpenApiConfig.java              springdoc OpenAPI metadata.
    │   └── RedisCacheConfig.java           @EnableCaching. RedisCacheManager, JSON serialization, 10-min TTL.
    ├── constants/
    │   ├── ExceptionMessageConstants.java  Error message string constants.
    │   ├── RequestMessageConstants.java    Success message string constants.
    │   └── ValidationConstants.java        Bean-validation message string constants.
    ├── data/
    │   └── EntityBase.java                 @MappedSuperclass with createdAt / updatedAt (JPA auditing).
    ├── event/
    │   ├── EventPublisherPort.java         Outbound port: void publish(Object event).
    │   ├── KafkaEventPublisherAdapter.java Primary impl — routes events to Kafka topics.
    │   └── ApplicationEventPublisherAdapter.java  Fallback impl — Spring in-process bus.
    ├── exception/
    │   ├── BusinessLayerException.java     Unchecked exception carrying an HttpStatus.
    │   └── GlobalExceptionHandler.java     @RestControllerAdvice: Exception (500), MethodArgumentNotValidException (400), BusinessLayerException (dynamic).
    ├── security/
    │   ├── CorsProperties.java             @ConfigurationProperties(prefix = "app.cors"). Holds allowed-origins list.
    │   ├── RateLimitProperties.java        @ConfigurationProperties(prefix = "app.rate-limit").
    │   ├── SecurityConfig.java             Profile-split filter chains (dev vs staging/prod). Wires CORS + rate limiting.
    │   └── SecurityFilter.java             IP blacklist (403) + rate limiting (429) via Redis.
    └── web/
        ├── ApiResponseBase.java            @Builder envelope: status, message, data<T>, traceId.
        ├── HealthController.java           GET /api/health — connectivity and service health check.
        └── HealthStatusResponse.java       Record DTO: status ("UP"), timestamp (Instant).

src/main/resources/
├── application.yml                         Base configuration (all profiles).
├── application-dev.yml                     Dev overrides: DDL update, SQL logging, OAuth2 disabled.
├── application-staging.yml                 Staging overrides: DDL validate, Flyway on, sampling 0.5.
├── application-prod.yml                    Prod overrides: DDL validate, Flyway on, sampling 0.1.
└── graphql/
    └── schema.graphqls                     Placeholder schema. Real types added per bounded context.

src/test/java/wfederico/pneumacare/
├── PneumacareApplicationTests.java         Context-load smoke test. @DisabledIfEnvironmentVariable(CI=true).
└── TestcontainersConfiguration.java        @TestConfiguration: PostgreSQL 17, Redis 7.4, Kafka-native 3.8.0.
```

---

## Shared Kernel

### config

| Bean | Class | Notes |
|---|---|---|
| `@EnableJpaAuditing` | `JpaAuditingConfig` | Activates `@CreatedDate` / `@LastModifiedDate` on `EntityBase`. |
| `ConcurrentKafkaListenerContainerFactory` | `KafkaConfig` | Inherits Spring Boot defaults via `ConcurrentKafkaListenerContainerFactoryConfigurer`, then attaches `DefaultErrorHandler`: 3 retries × 1 s (`FixedBackOff`), `DeadLetterPublishingRecoverer` routing failures to `<topic>.DLT`. |
| `ModelMapper` | `ModelMapperConfig` | Singleton. Used for DTO ↔ entity mapping. |
| `ObservedAspect` | `ObservabilityConfig` | Enables `@Observed` annotation for Micrometer per-method spans. |
| OpenAPI bean | `OpenApiConfig` | API title, version, and description for Swagger UI. |
| `RedisCacheManager` | `RedisCacheConfig` | `@EnableCaching`. String keys, `GenericJackson2JsonRedisSerializer` values, 10-minute default TTL. |

### constants

Pure string-constant classes (no instances). Reference from service and exception classes to avoid hardcoded literals.

| Class | Scope |
|---|---|
| `ExceptionMessageConstants` | Error messages (entity not found, validation failures, etc.). |
| `RequestMessageConstants` | Success messages (created, updated, deleted, etc.). |
| `ValidationConstants` | Bean-validation `message` attribute values. |

### data

`EntityBase` — `@MappedSuperclass` that all JPA entities should extend.

| Field | Annotation | Type |
|---|---|---|
| `createdAt` | `@CreatedDate` | `LocalDateTime` |
| `updatedAt` | `@LastModifiedDate` | `LocalDateTime` |

### event

See [Event Publishing](#event-publishing) for full detail.

| Class | Role |
|---|---|
| `EventPublisherPort` | Outbound port interface. Single method: `void publish(Object event)`. |
| `KafkaEventPublisherAdapter` | `@Primary`. Active when `app.kafka.enabled=true`. |
| `ApplicationEventPublisherAdapter` | `@ConditionalOnMissingBean`. Fallback when Kafka is disabled. |

### exception

| Class | Description |
|---|---|
| `BusinessLayerException` | `RuntimeException` carrying a `message` and an `HttpStatus`. Thrown from the service layer, caught and serialized by `GlobalExceptionHandler`. |
| `GlobalExceptionHandler` | `@RestControllerAdvice`. Three handlers: generic `Exception` → 500, `MethodArgumentNotValidException` → 400 with field errors, `BusinessLayerException` → dynamic status. All responses wrapped in `ApiResponseBase` with `traceId`. |

### security

| Class | Description |
|---|---|
| `CorsProperties` | `@ConfigurationProperties(prefix = "app.cors")`. Field: `allowedOrigins` (list). Populated per profile from YAML; overridable with `CORS_ALLOWED_ORIGINS` env var in staging/prod. |
| `RateLimitProperties` | `@ConfigurationProperties(prefix = "app.rate-limit")`. Fields: `threshold` (default 10), `windowSeconds` (default 60). |
| `SecurityConfig` | Registers two `SecurityFilterChain` beans: one for `dev` (all `/api/**` open, no OAuth2), one for `!dev` (OAuth2 JWT + scope-based rules). Both chains are stateless, CSRF-disabled, CORS-enabled, and inject `SecurityFilter`. Exposes `CorsConfigurationSource` bean used by both chains. |
| `SecurityFilter` | `OncePerRequestFilter`. Checks Redis `blacklist:{ip}` → 403 `ApiResponseBase`. Checks Redis `rate_limit:{ip}` counter → 429 `ApiResponseBase` when threshold exceeded. |

> **Note**: `SecurityConfig` uses `tools.jackson.databind.ObjectMapper` (Jackson 3.x, bundled with Spring Boot 4). Do not replace with `com.fasterxml.jackson.databind.ObjectMapper`.

### web

`ApiResponseBase<T>` — standard response envelope.

```java
ApiResponseBase.<MyDto>builder()
    .status(HttpStatus.OK.value())
    .message(RequestMessageConstants.FOUND)
    .data(dto)
    .traceId(MDC.get("traceId"))
    .build();
```

`HealthController` — exposes `GET /api/health` for frontend-backend connectivity checks. Returns an `ApiResponseBase<HealthStatusResponse>` with `status: "UP"` and an `Instant` timestamp. Permitted without authentication in all profiles.

`HealthStatusResponse` — immutable record DTO (`status`, `timestamp`) produced by `HealthController.health()` via the static factory `HealthStatusResponse.up()`.

---

## Event Publishing

All domain events are published through the `EventPublisherPort` outbound port. Which implementation is active depends on the `app.kafka.enabled` flag:

| Condition | Active Bean | Behaviour |
|---|---|---|
| `app.kafka.enabled=false` (default) | `ApplicationEventPublisherAdapter` | In-process Spring `ApplicationEventPublisher`. No broker needed. Use for local dev. |
| `app.kafka.enabled=true` | `KafkaEventPublisherAdapter` | Sends event as JSON to a derived Kafka topic. `admin.fail-fast=false` so startup does not fail if the broker is temporarily unreachable. |

### Topic naming convention

The Kafka topic is derived automatically from the event class name:

1. Strip trailing `Event` suffix (if present).
2. Convert CamelCase → kebab-case.
3. Prefix with `app.kafka.topics.prefix` (default: `pneumacare.events`).

| Event class | Derived topic |
|---|---|
| `PatientAdmittedEvent` | `pneumacare.events.patient-admitted` |
| `AssessmentCompleted` | `pneumacare.events.assessment-completed` |

### Error recovery

Consumer-side errors are handled by `KafkaConfig`'s `DefaultErrorHandler`:

- **3 retries** with a **1-second fixed back-off**.
- After exhausting retries the record is routed to `<original-topic>.DLT` via `DeadLetterPublishingRecoverer`.

---

## Configuration

### application.yml

| Section | Key Settings |
|---|---|
| `spring.application.name` | `pneumacare` |
| `spring.profiles.active` | `${SPRING_PROFILES_ACTIVE:dev}` |
| DataSource | PostgreSQL via `DB_HOST/PORT/NAME/USER/PASSWORD`. |
| JPA | `ddl-auto: update` (overridden per profile). `show-sql: true` (overridden). |
| Flyway | Disabled in base config; enabled per profile. |
| Redis | `${REDIS_HOST:localhost}:${REDIS_PORT:6379}`. |
| OAuth2 | JWT issuer URI via `${OAUTH2_ISSUER_URI:http://localhost:9000}`. |
| Kafka | Bootstrap servers `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`. `admin.fail-fast: false`. Producer: `JsonSerializer`. Consumer: `JsonDeserializer`, `group-id: pneumacare`, `auto-offset-reset: earliest`. `listener.observation-enabled: true`. |
| `app.kafka.enabled` | `${KAFKA_ENABLED:false}` — toggles the active `EventPublisherPort` implementation. |
| `app.kafka.topics.prefix` | `${KAFKA_TOPICS_PREFIX:pneumacare.events}` |
| Server | `${APP_PORT:8080}` |
| springdoc | API docs: `/v3/api-docs`. Swagger UI: `/swagger-ui.html`. |
| Actuator | Exposes `health`, `info`, `prometheus`, `metrics`. Health details: `always`. |
| OTLP | Metrics and traces to `${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}`. |
| Tracing | Sampling probability `1.0` (overridden per profile). |
| Rate limiting | `app.rate-limit.threshold` and `app.rate-limit.window-seconds`. |
| Log pattern | `[pneumacare,%mdc{traceId:-},%mdc{spanId:-}]` correlation prefix. |

### Profile Overrides

| Profile | `ddl-auto` | `flyway.enabled` | `show-sql` | Trace sampling | OAuth2 |
|---|---|---|---|---|---|
| `dev` | `update` | `false` | `true` | `1.0` | **Disabled** (`OAuth2ResourceServerAutoConfiguration` excluded) |
| `staging` | `validate` | `true` | `false` | `0.5` | Enabled |
| `prod` | `validate` | `true` | `false` | `0.1` | Enabled — health details `when-authorized` |

### Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `APP_PORT` | `8080` | Application HTTP port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile |
| `DB_HOST` | `localhost` | PostgreSQL hostname |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `mi_base_de_datos` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | _(no default — must be set)_ | Database password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `KAFKA_ENABLED` | `false` | `true` to activate Kafka event publisher |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `KAFKA_TOPICS_PREFIX` | `pneumacare.events` | Prefix for all derived topic names |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` | OTLP collector endpoint |
| `OTEL_SERVICE_NAME` | `pneumacare` | Service identifier in traces/metrics |
| `OAUTH2_ISSUER_URI` | `http://localhost:9000` | OAuth2 JWT issuer URI (staging/prod) |
| `RATE_LIMIT_THRESHOLD` | `10` | Max requests per window per IP |
| `RATE_LIMIT_WINDOW` | `60` | Rate-limit window in seconds |
| `CORS_ALLOWED_ORIGINS` | _(none)_ | Comma-separated allowed origins for CORS in staging/prod (e.g. `https://app.example.com`) |

Copy `.env.example` to `.env` and adjust before running `docker compose up`.

---

## Docker Compose Services

Two Compose files are provided:

| File | Purpose |
|---|---|
| `docker-compose.dev.yml` | **Database only** — PostgreSQL 17 on port 5432 (localhost-bound). Use this for local development when you only need the database. |
| `compose.yaml` | **Full stack** — all services (app, PostgreSQL 17, Redis, Kafka, Grafana LGTM). |

### `docker-compose.dev.yml` services

| Service | Image | Port | Volume |
|---|---|---|---|
| `postgres` | `postgres:17` | `127.0.0.1:5432` | `postgres_data_dev` (persists across restarts; distinct from the `postgres_data` volume used by `compose.yaml`) |

```bash
cp .env.example .env   # set DB_NAME / DB_USER / DB_PASSWORD (required — no insecure defaults)
docker compose -f docker-compose.dev.yml up -d   # AC1: PostgreSQL 17 listening on 127.0.0.1:5432
```

### `compose.yaml` services

| Service | Container | Image | Purpose | Healthcheck |
|---|---|---|---|---|
| `app` | `pneumacare-app` | Built from `Dockerfile` | Spring Boot application | `wget /actuator/health` (10 s interval, 30 s start period) |
| `postgres` | `pneumacare-postgres` | `postgres:17` | Primary datastore | `pg_isready` |
| `redis` | `pneumacare-redis` | `redis:7.4` | Rate limiting, caching, IP blacklist | None |
| `kafka` | `pneumacare-kafka` | `apache/kafka:3.9.2` | Event streaming, KRaft mode (no ZooKeeper) | `/opt/kafka/bin/kafka-topics.sh --list` |
| `grafana-lgtm` | `pneumacare-grafana` | `grafana/otel-lgtm:0.9.1` | Grafana + Loki + Tempo + Mimir | None |

The `app` service starts only after `postgres` (healthy) and `kafka` (healthy). Kafka has `KAFKA_ENABLED=true` and `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` injected automatically by `compose.yaml`.

Persistent volumes: `postgres_data_dev` (database-only stack), `postgres_data` / `redis_data` / `kafka_data` (full stack).

---

## Dockerfile

Multi-stage build.

**Stage 1 — Build** (`maven:3.9-eclipse-temurin-17`):
- Resolves dependencies offline (`mvn dependency:go-offline -B`).
- Packages the fat JAR (`mvn clean package -DskipTests -B`).

**Stage 2 — Runtime** (`eclipse-temurin:17-jre`):
- Creates system group/user `pneumacare` (gid/uid `1001`). Container does **not** run as root.
- Copies the JAR with `--chown=pneumacare:pneumacare`.
- `USER pneumacare` before `ENTRYPOINT`.
- Exposes port `8080`.

A `.dockerignore` file excludes `.git`, `.github`, `target/`, `.env*`, `grafana/`, `SOPORTE-UTI-RESPI/`, and `*.md` from the build context.

---

## Security

Security behaviour is split by Spring profile.

### `dev` profile

All `/api/**` endpoints are **open** (no token required). OAuth2 resource server auto-configuration is excluded entirely. Useful for local development and integration testing without a running authorization server.

### `staging` and `prod` profiles

OAuth2 JWT validation is active (`spring.security.oauth2.resourceserver.jwt`). Scope-based authorization rules:

| Method | Path pattern | Required authority |
|---|---|---|
| `GET` | `/api/health` | None (permit all) |
| `GET` | `/api/**` | `SCOPE_read` |
| `POST`, `PUT`, `PATCH`, `DELETE` | `/api/**` | `SCOPE_write` |
| Any | `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` | None (permit all) |

All chains are **stateless** (no sessions) and **CSRF-disabled**.

### CORS

CORS is enabled on both filter chains via a shared `CorsConfigurationSource` bean. Allowed origins are loaded from `app.cors.allowed-origins` (YAML) or the `CORS_ALLOWED_ORIGINS` environment variable.

| Setting | Value |
|---|---|
| Allowed origins | Profile-specific (see below) |
| Allowed methods | `GET POST PUT PATCH DELETE OPTIONS` |
| Allowed headers | `Authorization`, `Content-Type`, `Accept`, `X-Requested-With` |
| Allow credentials | `false` (stateless JWT API — no cookies) |
| Preflight max-age | `3600 s` |

| Profile | Allowed origins |
|---|---|
| `dev` | `http://localhost:4200` |
| `staging` / `prod` | `${CORS_ALLOWED_ORIGINS}` (env var, comma-separated for multiple origins) |

### SecurityFilter (all profiles)

Applied before `UsernamePasswordAuthenticationFilter`:

| Check | Redis key | Response |
|---|---|---|
| IP blacklist | `blacklist:{ip}` | HTTP 403 + `ApiResponseBase` JSON body |
| Rate limiting | `rate_limit:{ip}` | HTTP 429 + `ApiResponseBase` JSON body when `threshold` exceeded within `windowSeconds` |

---

## Observability

| Signal | Mechanism | Destination |
|---|---|---|
| Metrics | Micrometer OTLP + `/actuator/prometheus` | Grafana Mimir |
| Traces | OTLP (Spring Boot OpenTelemetry starter) | Grafana Tempo |
| Logs | Structured stdout with `[service,traceId,spanId]` prefix | Grafana Loki |

- `ObservedAspect` bean enables `@Observed(name = "...")` on service classes for automatic per-method observation spans.
- `traceId` from `MDC` is injected into every `ApiResponseBase` response.
- Sampling probability: `1.0` (dev) → `0.5` (staging) → `0.1` (prod).
- `DatadogMetricsExportAutoConfiguration` excluded at startup to prevent Datadog API-key errors.
- Grafana dashboard provisioned from `grafana/dashboards/` with job label `pneumacare` and Kafka consumer-lag / throughput panels.

Grafana UI is available at `http://localhost:3000` when running the full compose stack.

---

## GraphQL

Schema file: `src/main/resources/graphql/schema.graphqls`

Currently a placeholder (`type Query { _placeholder: String }`). Real domain types and resolvers will be added alongside each bounded context.

---

## Testing

| Class | Scope | Notes |
|---|---|---|
| `PneumacareApplicationTests` | Context load | `@SpringBootTest`. `@DisabledIfEnvironmentVariable(named = "CI", matches = "true")` — automatically skipped in GitHub Actions. |
| `TestcontainersConfiguration` | `@TestConfiguration` | `@ServiceConnection` beans for PostgreSQL 17, Redis 7.4, and `apache/kafka-native:3.8.0`. Import into any `@SpringBootTest` class that needs real infrastructure. |

Tests that exercise Kafka must also set `app.kafka.enabled=true`:

```java
@SpringBootTest(properties = "app.kafka.enabled=true")
@Import(TestcontainersConfiguration.class)
class MyKafkaTest { ... }
```

Run the test suite:

```bash
./mvnw test          # unit tests
./mvnw verify        # unit + integration tests (requires Docker for Testcontainers)
```

---

## CI/CD

Three workflow files live in `.github/workflows/`. Credentials are supplied via **GitHub Secrets** with safe fallback values so workflows also pass for forked PRs where secrets are unavailable.

### `build.yml` — Build pipeline

**Triggers**: push or pull request targeting `main` or `develop`.

| Step | Detail |
|---|---|
| PostgreSQL 17 service container | Credentials from `DB_NAME`, `DB_USER`, `DB_PASSWORD` secrets (fallback: `pneumacare_ci` / `postgres` / `ci_postgres_pw`) |
| Redis 7.4 service container | Health-checked with `redis-cli ping` |
| JDK 17 Temurin | Maven dependency cache enabled |
| `./mvnw clean install -B` | Full build + test lifecycle. Any JUnit failure exits non-zero and blocks merge. |
| Upload Surefire reports | Runs `if: always()` so failures are visible in the Actions UI |

A `timeout-minutes: 5` gate enforces the 5-minute execution threshold. Concurrent runs for the same branch are cancelled automatically. Credentials are defined once at job level and inherited by all steps; service containers repeat the same fallback expressions (GitHub Actions does not expose job-level `env` inside service-container config blocks).

### `ci.yml` — Full integration pipeline

**Triggers**: push to `main`, `develop`, `feat/**`; version tags `v*.*.*`; PRs to `main`/`develop`.

**`build` job** (always runs):

1. Starts PostgreSQL 17 and Redis 7.4 service containers (credentials from secrets with fallbacks).
2. Sets up JDK 17 (Temurin) with Maven dependency cache.
3. Runs `./mvnw verify -B`.
4. Uploads Surefire XML reports as a build artifact (`if: always()`).
5. Builds the Docker image as a smoke test using `docker/build-push-action@v6` with GitHub Actions layer cache (`type=gha`).

**`docker-push` job** (runs only on push to `main` or a `v*.*.*` tag, after `build` passes):

1. Logs in to GitHub Container Registry (GHCR) with `GITHUB_TOKEN`.
2. Extracts semver tags and SHA tag via `docker/metadata-action@v5`.
3. Builds and pushes to `ghcr.io/<owner>/pneumacare` with the same GHA layer cache.

### `sast.yml` — Static analysis

**Triggers**: push or PR to `main`/`develop`, plus a weekly scheduled scan (Monday 02:00 UTC).

| Job | Tool | Purpose |
|---|---|---|
| `codeql` | GitHub CodeQL (`security-and-quality` query suite) | Scans Java bytecode for security vulnerabilities and code-quality issues; uploads SARIF to the Security tab |
| `dependency-review` | `actions/dependency-review-action@v4` | PRs only — blocks merge if any new dependency introduces a HIGH or CRITICAL CVE |

### `opencode.yml` — AI agent trigger

Runs the OpenCode agent when a comment body contains `/oc` or `/opencode`. Requires `OPENCODE_API_KEY` secret. Permissions: `contents`, `pull-requests`, and `issues` set to `write` so the agent can create branches, commit fixes, open PRs, and post replies.

### GitHub Secrets

Secrets are optional — workflows include safe fallback values so they run on forked PRs without any secrets configured. When secrets are set, they override the fallbacks.

| Secret | Fallback (CI only) | Purpose |
|---|---|---|
| `DB_NAME` | `pneumacare_ci` | PostgreSQL database name |
| `DB_USER` | `postgres` | PostgreSQL user |
| `DB_PASSWORD` | `ci_postgres_pw` | PostgreSQL password — set a real secret to avoid using the fallback in your environment |

> **Note**: `DB_HOST`, `DB_PORT`, `REDIS_HOST`, and `REDIS_PORT` are not secrets — they are fixed topology values (`localhost` / standard ports) defined directly in the workflow `env` blocks.

---

## Running

### Prerequisites

- Docker and Docker Compose v2
- Java 17 (for local development without Docker)
- Maven 3.9+ (or use the included `./mvnw` wrapper)

### Full stack with Docker Compose

```bash
cp .env.example .env    # fill in real values
docker compose up --build
```

| URL | Service |
|---|---|
| `http://localhost:8080` | Application |
| `http://localhost:8080/api/health` | Custom health check (connectivity check, no auth required) |
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/actuator/health` | Actuator health (infrastructure details) |
| `http://localhost:8080/actuator/prometheus` | Prometheus metrics |
| `http://localhost:3000` | Grafana |

### Local development (no Docker)

Set `SPRING_PROFILES_ACTIVE=dev` (default). Requires PostgreSQL and Redis running locally on default ports.

```bash
./mvnw spring-boot:run
```

Kafka is **not** required locally (`app.kafka.enabled=false` by default). The `ApplicationEventPublisherAdapter` fallback handles all event publishing in-process.

---

## Releases and Docker Image

This project follows [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`). Current version: **1.1.0**.

### Creating a release

```bash
git tag -a v1.1.0 -m "release: v1.1.0 — initial scaffold"
git push origin v1.1.0
```

Pushing a `v*.*.*` tag triggers the CI pipeline which runs tests, then builds and pushes to GHCR with four tags:

| Tag | Description |
|---|---|
| `1.1.0` | Exact semantic version |
| `1.1` | Latest patch within this minor |
| `sha-<commit>` | Pinned to commit SHA |
| `latest` | Most recent `main` push or tag |

### Pulling the image

```bash
docker pull ghcr.io/wfederico97/pneumacare:1.1.0
docker pull ghcr.io/wfederico97/pneumacare:latest
```
