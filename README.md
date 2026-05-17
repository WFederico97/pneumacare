# backend-java-core-template

Spring Boot 4.0.3 REST microservice scaffold implementing a layered hexagonal architecture. Java 17. Maven build. Dockerized with PostgreSQL, Redis, Kafka, and Grafana LGTM (Loki, Grafana, Tempo, Mimir) for full observability.

---

## Table of Contents

- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Layer Specification](#layer-specification)
  - [domain](#domain)
  - [application](#application)
  - [core](#core)
  - [infra](#infra)
- [Configuration](#configuration)
  - [application.yml](#applicationyml)
  - [Environment Variables](#environment-variables)
  - [Docker Compose Services](#docker-compose-services)
  - [Dockerfile](#dockerfile)
- [API Surface](#api-surface)
- [Security](#security)
- [Observability](#observability)
- [GraphQL](#graphql)
- [Testing](#testing)
- [Known Issues](#known-issues)
- [TODO](#todo)
- [Releases & Docker Image](#releases--docker-image)
- [Running](#running)

---

## Architecture

Hexagonal (ports and adapters) variant with four top-level packages:

```
domain        Pure domain model, domain events, and domain exceptions. No framework imports (except JPA annotations on entities).
application   Use-case orchestration. Inbound ports (use-case interfaces), outbound ports (persistence contracts), and service implementations.
core          Cross-cutting concerns: configuration beans, constants, base classes, exception handling, security, messaging, caching, observability, web response envelope.
infra         Framework-bound adapters: inbound (REST controllers, GraphQL resolvers, DTOs) and outbound (JPA repositories, persistence adapters).
```

Dependency rule: `infra -> application -> domain`. `core` is referenced by all layers for shared infrastructure.

---

## Project Structure

```
src/main/java/wfederico/backendjavacoretemplate/
|
+-- BackendJavaCoreTemplateApplication.java          Entry point. @EnableJpaAuditing. Excludes DatadogMetricsExportAutoConfiguration.
|
+-- domain/
|   +-- event/
|   |   +-- PlayerEvent.java                         Domain event DTO (PLAYER_CREATED, PLAYER_UPDATED, PLAYER_DELETED).
|   +-- exception/
|   |   +-- BusinessLayerException.java              RuntimeException carrying HttpStatus.
|   +-- model/
|       +-- player/
|       |   +-- PlayerEntity.java                    JPA @Entity "players". @ManyToOne team. Extends EntityBase. Indexed columns.
|       +-- team/
|           +-- TeamEntity.java                      JPA @Entity "teams". @OneToMany players. Extends EntityBase.
|
+-- application/
|   +-- port/
|   |   +-- in/
|   |   |   +-- PlayerUseCase.java                   Inbound port interface. Defines all Player CRUD operations.
|   |   +-- out/
|   |       +-- PlayerPersistencePort.java           Outbound port for Player persistence.
|   |       +-- TeamPersistencePort.java             Outbound port for Team persistence.
|   +-- service/
|       +-- PlayerService.java                       Implements PlayerUseCase. @Observed. @Cacheable/@CacheEvict. Publishes domain events via Kafka.
|
+-- core/
|   +-- config/
|   |   +-- ModelMapperConfig.java                   Singleton ModelMapper bean.
|   |   +-- ObservabilityConfig.java                 ObservedAspect bean for @Observed AOP support.
|   |   +-- OpenApiConfig.java                       springdoc OpenAPI metadata.
|   |   +-- RedisCacheConfig.java                    @EnableCaching. RedisCacheManager with JSON serialization, 10min TTL.
|   +-- constants/
|   |   +-- ExceptionMessageConstants.java           Error message literals (PLAYER_NOT_FOUND, TEAM_NOT_FOUND, etc.).
|   |   +-- RequestMessageConstants.java             Success message literals.
|   |   +-- ValidationConstants.java                 Bean-validation message literals.
|   +-- data/
|   |   +-- EntityBase.java                          @MappedSuperclass: createdAt, updatedAt (JPA auditing).
|   +-- exception/
|   |   +-- GlobalExceptionHandler.java              @RestControllerAdvice: Exception, MethodArgumentNotValidException, BusinessLayerException.
|   +-- messaging/
|   |   +-- DomainEventPublisher.java                Kafka producer. Publishes domain events to configured topics.
|   |   +-- KafkaConfig.java                         ProducerFactory, ConsumerFactory, KafkaTemplate, ListenerContainerFactory. Group-id + trusted packages configured.
|   |   +-- KafkaTopicConfig.java                    NewTopic beans (player-events, 3 partitions, 1 replica).
|   |   +-- KafkaTopics.java                         Topic name constants.
|   |   +-- PlayerEventConsumer.java                 @KafkaListener consuming player-events topic.
|   +-- security/
|   |   +-- RateLimitProperties.java                 @ConfigurationProperties(prefix = "app.rate-limit"). Configurable threshold and window.
|   |   +-- SecurityConfig.java                      SecurityFilterChain. CSRF disabled. Stateless. OAuth2 resource server JWT. Scope-based authorization rules.
|   |   +-- SecurityFilter.java                      OncePerRequestFilter: IP blacklist (403 with JSON body), rate limiting (429, configurable via properties).
|   +-- web/
|       +-- ApiResponseBase.java                     Generic envelope: status, message, data<T>, traceId.
|
+-- infra/
    +-- adapter/
        +-- in/
        |   +-- controller/
        |   |   +-- PlayerController.java            @RestController /api/v1/players. Full CRUD. Injects PlayerUseCase (port).
        |   +-- dto/
        |   |   +-- PlayerRequestDTO.java            Inbound payload with team_id. @NotNull + @Pattern validations.
        |   |   +-- PlayerPatchDTO.java              Partial update payload. All fields optional. Includes team_id.
        |   |   +-- PlayerResponseDTO.java           Outbound payload with team_id. @JsonProperty snake_case.
        |   +-- graphql/
        |       +-- PlayerQueryResolver.java         @Controller with @QueryMapping/@MutationMapping. Injects PlayerUseCase.
        +-- out/
            +-- repository/
                +-- PlayerPersistenceAdapter.java    Implements PlayerPersistencePort. Delegates to PlayerRepository.
                +-- PlayerRepository.java            JpaRepository<PlayerEntity, Long>.
                +-- TeamPersistenceAdapter.java      Implements TeamPersistencePort. Delegates to TeamRepository.
                +-- TeamRepository.java              JpaRepository<TeamEntity, Long>.
```

---

## Layer Specification

### domain

| Component | Description |
|---|---|
| `PlayerEntity` | JPA entity mapped to `players` table. Fields: `id` (IDENTITY), `firstName`, `lastName`, `position`, `alterPosition`, `team` (@ManyToOne). Indexes on `last_name`, `position`, `team_id`. Inherits `createdAt`/`updatedAt` from `EntityBase`. |
| `TeamEntity` | JPA entity mapped to `teams` table. Fields: `id` (IDENTITY), `name`, `city`, `country`, `players` (@OneToMany). Inherits `createdAt`/`updatedAt` from `EntityBase`. |
| `PlayerEvent` | Domain event DTO. Static factories: `created(id)`, `updated(id)`, `deleted(id)`. Published to Kafka on write operations. |
| `BusinessLayerException` | Unchecked exception wrapping a message and `HttpStatus`. Thrown from service layer, caught by `GlobalExceptionHandler`. |

### application

| Component | Description |
|---|---|
| `PlayerUseCase` | Inbound port interface defining all Player CRUD operations. Implemented by `PlayerService`. Injected by `PlayerController` and `PlayerQueryResolver`. |
| `PlayerPersistencePort` | Outbound port interface for Player persistence. Implemented by `PlayerPersistenceAdapter`. |
| `TeamPersistencePort` | Outbound port interface for Team persistence. Implemented by `TeamPersistenceAdapter`. |
| `PlayerService` | `@Service` implementing `PlayerUseCase`. Dependencies: `PlayerPersistencePort`, `TeamPersistencePort`, `ModelMapper`, `DomainEventPublisher`. `@Observed(name = "player.service")` for micrometer metrics. `@Cacheable`/`@CacheEvict` on read/write operations via Redis. Publishes `PlayerEvent` on create/update/delete. |

### core

| Component | Description |
|---|---|
| `ModelMapperConfig` | Singleton `ModelMapper` bean. |
| `ObservabilityConfig` | `ObservedAspect` bean enabling `@Observed` annotation processing. |
| `OpenApiConfig` | springdoc OpenAPI metadata. |
| `RedisCacheConfig` | `@EnableCaching`. `RedisCacheManager` with `StringRedisSerializer` keys, `GenericJackson2JsonRedisSerializer` values, 10-minute TTL. |
| `EntityBase` | `@MappedSuperclass` with `@CreatedDate`/`@LastModifiedDate`. |
| `GlobalExceptionHandler` | Three handlers: generic `Exception` (500), `MethodArgumentNotValidException` (400), `BusinessLayerException` (dynamic). All wrapped in `ApiResponseBase` with traceId. |
| `DomainEventPublisher` | Kafka producer. `publish(Object event)` sends to `player-events` topic. |
| `KafkaConfig` | Producer/consumer factory beans. Consumer includes `GROUP_ID_CONFIG` and `TRUSTED_PACKAGES`. `ConcurrentKafkaListenerContainerFactory` bean. `@SuppressWarnings("removal")` on deprecated serializers. |
| `KafkaTopicConfig` | `NewTopic` bean for `player-events` (3 partitions, 1 replica). |
| `PlayerEventConsumer` | `@KafkaListener` on `player-events`. Logs consumed events. |
| `RateLimitProperties` | `@ConfigurationProperties(prefix = "app.rate-limit")`. Fields: `threshold` (default 10), `windowSeconds` (default 60). |
| `SecurityConfig` | CSRF disabled. Stateless sessions. OAuth2 resource server JWT validation (`Customizer.withDefaults()`). Scope-based authorization: `GET /api/**` requires `SCOPE_read`, `POST/PUT/PATCH/DELETE /api/**` requires `SCOPE_write`. Swagger/actuator whitelisted. Registers `SecurityFilter`. |
| `SecurityFilter` | Blacklist check: `blacklist:{ip}` → 403 with `ApiResponseBase` JSON body. Rate limiting: `rate_limit:{ip}` → 429 with configurable threshold/window from `RateLimitProperties`. |
| `ApiResponseBase<T>` | `@Builder` generic envelope: `status`, `message`, `data`, `traceId`. |

### infra

| Component | Description |
|---|---|
| `PlayerController` | `@RestController` at `/api/v1/players`. Injects `PlayerUseCase` (inbound port). Full CRUD with pagination, OpenAPI annotations. |
| `PlayerQueryResolver` | GraphQL controller. `@QueryMapping` for `players` (paged) and `player(id)`. `@MutationMapping` for create/update/patch/delete. Injects `PlayerUseCase`. |
| `PlayerPersistenceAdapter` | `@Component` implementing `PlayerPersistencePort`. Delegates to `PlayerRepository`. |
| `TeamPersistenceAdapter` | `@Component` implementing `TeamPersistencePort`. Delegates to `TeamRepository`. |
| DTOs | `PlayerRequestDTO` (full, with `team_id`), `PlayerPatchDTO` (partial, with `team_id`), `PlayerResponseDTO` (outbound, with `team_id`). |

---

## Configuration

### application.yml

| Section | Key Configuration |
|---|---|
| Profiles | Active profile via `SPRING_PROFILES_ACTIVE` env var. Default: `dev`. |
| DataSource | PostgreSQL via env vars. Base `ddl-auto: update`. |
| Flyway | Disabled in base config. Enabled in `staging`/`prod` profiles. Migrations in `db/migration/`. |
| Redis | Host/port via env vars. |
| Kafka | `bootstrap-servers` via env var. Consumer `group-id`, `auto-offset-reset: earliest`. |
| OAuth2 | `spring.security.oauth2.resourceserver.jwt.issuer-uri` via `OAUTH2_ISSUER_URI`. |
| Server | Port from `APP_PORT` (default 8080). |
| springdoc | API docs at `/v3/api-docs`. Swagger UI at `/swagger-ui.html`. |
| Actuator | `health`, `info`, `prometheus`, `metrics`. Health detail: `always`. |
| OTLP | Metrics and traces to `OTEL_EXPORTER_OTLP_ENDPOINT`. |
| Tracing | Sampling: `1.0` (overridden per profile). |
| Rate Limiting | `app.rate-limit.threshold` and `app.rate-limit.window-seconds` via env vars. |

### Profile Overrides

| Profile | `ddl-auto` | `flyway.enabled` | `show-sql` | `sampling.probability` |
|---|---|---|---|---|
| `dev` | `update` | `false` | `true` | `1.0` |
| `staging` | `validate` | `true` | `false` | `0.5` |
| `prod` | `validate` | `true` | `false` | `0.1` |

### Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `APP_PORT` | `8080` | Application port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile |
| `DB_HOST` | `postgres` | PostgreSQL hostname |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `mi_base_de_datos` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | `admin` | Database password |
| `REDIS_HOST` | `redis` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `KAFKA_BROKER` | `kafka:9092` | Kafka bootstrap server |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://grafana-lgtm:4318` | OpenTelemetry collector |
| `OTEL_SERVICE_NAME` | `my-microservice` | Service identifier |
| `OAUTH2_ISSUER_URI` | `http://localhost:9000` | OAuth2 JWT issuer URI |
| `RATE_LIMIT_THRESHOLD` | `10` | Max requests per window |
| `RATE_LIMIT_WINDOW` | `60` | Rate limit window in seconds |

### Docker Compose Services

| Service | Image | Purpose | Healthcheck |
|---|---|---|---|
| `app` | Built from `Dockerfile` | Spring Boot application | `wget` against `/actuator/health` (10s interval, 30s start period) |
| `postgres` | `postgres:17` | Primary datastore | `pg_isready` |
| `redis` | `redis:7.4` | Rate limiting / caching / blacklist | None |
| `kafka` | `apache/kafka:3.9.0` | Event streaming (KRaft mode) | None |
| `grafana-lgtm` | `grafana/otel-lgtm:0.9.1` | Grafana + Loki + Tempo + Mimir | None |

### Dockerfile

Multi-stage build:
1. **Build**: `maven:3.9-eclipse-temurin-17`. `mvn clean package -DskipTests`.
2. **Runtime**: `eclipse-temurin:17-jre`. Fat JAR on port 8080.

---

## API Surface

Base path: `/api/v1/players`

| Method | Path | Query Params | Request Body | Response | Status |
|---|---|---|---|---|---|
| `GET` | `/api/v1/players` | `page`, `size`, `sortBy`, `direction` | -- | `ApiResponseBase<Page<PlayerResponseDTO>>` | 200 / 404 |
| `GET` | `/api/v1/players/{id}` | -- | -- | `ApiResponseBase<PlayerResponseDTO>` | 200 / 404 |
| `POST` | `/api/v1/players` | -- | `PlayerRequestDTO` | `ApiResponseBase<PlayerResponseDTO>` | 201 / 400 |
| `PUT` | `/api/v1/players/{id}` | -- | `PlayerRequestDTO` | `ApiResponseBase<PlayerResponseDTO>` | 200 / 400 / 404 |
| `PATCH` | `/api/v1/players/{id}` | -- | `PlayerPatchDTO` | `ApiResponseBase<PlayerResponseDTO>` | 200 / 400 / 404 |
| `DELETE` | `/api/v1/players/{id}` | -- | -- | `ApiResponseBase<Void>` | 200 / 404 |

Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## Security

- CSRF disabled (stateless API).
- Session policy: `STATELESS`.
- **OAuth2 resource server JWT** validation via `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
- **Scope-based authorization**: `GET /api/**` → `SCOPE_read`, `POST/PUT/PATCH/DELETE /api/**` → `SCOPE_write`. Swagger/actuator paths whitelisted.
- `SecurityFilter` (pre-auth filter):
  - **IP blacklist**: `blacklist:{ip}` key in Redis → HTTP 403 with `ApiResponseBase` JSON body.
  - **Rate limiting**: `rate_limit:{ip}` key in Redis. Configurable threshold (`app.rate-limit.threshold`) and window (`app.rate-limit.window-seconds`) via `RateLimitProperties`.

---

## Observability

| Signal | Exporter | Destination |
|---|---|---|
| Metrics | OTLP + Prometheus (`/actuator/prometheus`) + `@Observed` per-method metrics | Grafana Mimir |
| Traces | OTLP | Grafana Tempo |
| Logs | Stdout with traceId/spanId correlation | Grafana Loki |

- `ObservedAspect` bean enables `@Observed` annotation on `PlayerService` for granular per-method observation spans.
- Sampling probability: `1.0` (dev), `0.5` (staging), `0.1` (prod).
- `traceId` injected into all `ApiResponseBase` responses via `MDC.get("traceId")`.
- Datadog metrics auto-configuration excluded via `@SpringBootApplication(excludeName = ...)`.

---

## GraphQL

Schema: `src/main/resources/graphql/schema.graphqls`

| Operation | Type | Arguments |
|---|---|---|
| `players` | Query | `page`, `size`, `sortBy`, `direction` |
| `player` | Query | `id: ID!` |
| `createPlayer` | Mutation | `input: PlayerInput!` |
| `updatePlayer` | Mutation | `id: ID!`, `input: PlayerInput!` |
| `patchPlayer` | Mutation | `id: ID!`, `input: PlayerPatchInput!` |
| `deletePlayer` | Mutation | `id: ID!` |

DGS codegen generates Java types from `src/main/resources/graphql-client/remote-schema.graphqls` into `wfederico.backendjavacoretemplate.codegen`.

---

## Testing

| Test Class | Scope | Strategy |
|---|---|---|
| `PlayerServiceTest` | Unit | Mockito mocks for all ports and dependencies. 12 tests covering all CRUD, team resolution, and error paths. |
| `PlayerControllerIntegrationTest` | `@WebMvcTest` | `MockMvc` with `@MockitoBean PlayerUseCase`. Security filters disabled. Tests HTTP status, JSON structure, validation. |
| `PlayerRepositoryTest` | `@DataJpaTest` | `TestEntityManager` + `PlayerRepository`. Tests save, find, delete, team relationship. |
| `TestcontainersConfiguration` | `@TestConfiguration` | `@ServiceConnection` beans for PostgreSQL 17, Redis 7.4, Kafka 3.9 via Testcontainers. |

Asciidoctor/REST Docs: plugin pending Spring Boot 4 compatibility. `spring-restdocs-mockmvc` dependency present.

---

## Known Issues

1. **`SecurityConfig`/`SecurityFilter`**: imports `tools.jackson.databind.ObjectMapper` (Jackson 3.x). Do not change to `com.fasterxml.jackson.databind.ObjectMapper`.
2. **Postgres volume**: mounted at `/var/lib/postgresql`. Delete volume on major PostgreSQL image upgrade.
3. **`@SuppressWarnings("removal")`**: `JsonSerializer`/`JsonDeserializer` in Spring Kafka are deprecated. No stable replacement available.

---

## TODO

### Architecture / Structure

- [x] Define inbound port interfaces in `application.port.in` (`PlayerUseCase`) and have `PlayerService` implement them.
- [x] Define outbound port interfaces in `application.port.out` (`PlayerPersistencePort`, `TeamPersistencePort`) and implement via adapters.
- [x] Remove `infra.entity` package.
- [x] Decouple `PlayerService` from `PlayerRepository` by injecting the outbound port interface.

### API / Controller

- [x] Expose `GET /api/v1/players` endpoint with pagination.
- [x] Implement `PUT /api/v1/players/{id}` (update).
- [x] Implement `DELETE /api/v1/players/{id}` (delete).
- [x] Implement `PATCH /api/v1/players/{id}` (partial update).
- [x] Add pagination support (`Pageable`, `Page<PlayerResponseDTO>`) to list endpoint.

### Security

- [x] Replace `anyRequest().permitAll()` with scope-based authorization rules (`SCOPE_read`, `SCOPE_write`).
- [x] Implement OAuth2 resource server JWT validation.
- [x] Return `ApiResponseBase` JSON body on blacklist 403 response.
- [x] Make rate-limit threshold and window configurable via `RateLimitProperties`.

### Messaging / Kafka

- [x] Implement Kafka producer service (`DomainEventPublisher`).
- [x] Implement Kafka consumer listener (`PlayerEventConsumer` with `@KafkaListener`).
- [x] Add topic configuration beans (`KafkaTopicConfig`).
- [x] Add consumer `group-id` configuration to `KafkaConfig` consumer factory.
- [x] `@SuppressWarnings("removal")` present; replace `JsonSerializer`/`JsonDeserializer` when stable alternatives ship.

### GraphQL

- [x] Add GraphQL schema files to `src/main/resources/graphql/`.
- [x] Add remote service schemas to `src/main/resources/graphql-client/` for DGS codegen.
- [x] Implement GraphQL resolvers (`PlayerQueryResolver`).

### Observability

- [x] Resolve Datadog `apiKey` startup error: excluded auto-configuration via `@SpringBootApplication(excludeName = ...)`.
- [x] Configure sampling probability < 1.0 for production profiles (`application-prod.yml`).
- [x] Add `@Observed` on `PlayerService` with `ObservedAspect` bean.
- [ ] Verify OTLP traces appear in Grafana Tempo (manual validation).
- [ ] Verify OTLP metrics appear in Grafana Mimir (manual validation).
- [ ] Verify log correlation (traceId/spanId) in Grafana Loki (manual validation).

### Data / Persistence

- [x] Implement `TeamEntity` in `domain.model.team`.
- [x] Define entity relationships (`@ManyToOne`, `@OneToMany`) between `PlayerEntity` and `TeamEntity`.
- [x] Add Flyway migrations (`V1` teams, `V2` players, `V3` indexes). Enabled in staging/prod profiles.
- [x] Add database indexes on frequently queried columns (`last_name`, `position`, `team_id`).
- [x] Implement Redis caching (`@Cacheable`/`@CacheEvict`) on read/write operations.

### Testing

- [x] Add unit tests for `PlayerService` (12 test cases).
- [x] Add integration tests for `PlayerController` (`@WebMvcTest` + `MockMvc`).
- [x] Add repository tests (`@DataJpaTest`).
- [x] Configure Testcontainers for PostgreSQL, Redis, Kafka.
- [ ] Implement Spring REST Docs snippets (Asciidoctor plugin pending Spring Boot 4 compatibility).

### Build / Deployment

- [x] Add `.env` to `.gitignore`.
- [x] Add Spring Boot profiles (`dev`, `staging`, `prod`) with profile-specific `application-{profile}.yml`.
- [x] Configure CI/CD pipeline (`.github/workflows/ci.yml`).
- [x] Add health check to `app` service in `compose.yaml`.
- [x] Pin Docker image versions (`postgres:17`, `redis:7.4`, `apache/kafka:3.9.0`, `grafana/otel-lgtm:0.9.1`).
- [x] Publish Docker image to GitHub Container Registry (GHCR) on version tag push.

---

## Releases & Docker Image

This project follows [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`).

### Creating a Release

```bash
# Tag the release
git tag -a v1.0.0 -m "release: v1.0.0 — initial stable template"
git push origin v1.0.0
```

Pushing a `v*.*.*` tag triggers the CI pipeline which:
1. Runs the full test suite (`./mvnw verify`)
2. Builds the Docker image
3. Pushes to **GitHub Container Registry** with tags: `1.0.0`, `1.0`, `sha-<commit>`, `latest`

### Pulling the Image

```bash
docker pull ghcr.io/wfederico97/backend-java-template:1.0.0
docker pull ghcr.io/wfederico97/backend-java-template:latest
```

### Image Tags

| Tag | Description |
|---|---|
| `1.0.0` | Exact semantic version |
| `1.0` | Latest patch within minor |
| `sha-abc1234` | Pinned to commit SHA |
| `latest` | Most recent `main` push or tag |

---

## Running

### Prerequisites

- Docker and Docker Compose
- Java 17 (for local development without Docker)
- Maven 3.9+ (or use included `mvnw`)

### Docker Compose

```bash
cp .env.example .env    # adjust values as needed
docker compose up --build
```

Services:
- Application: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Grafana: `http://localhost:3000`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`

### Local (without Docker)

Requires PostgreSQL, Redis, and Kafka running locally. Set environment variables or rely on `application.yml` defaults.

```bash
./mvnw spring-boot:run
```

### Running Tests

```bash
./mvnw test                          # unit tests only
./mvnw verify                        # full build + integration tests
```

Testcontainers requires Docker running for integration tests.
