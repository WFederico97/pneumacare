# Database Migrations — PneumaCare

## Overview

PneumaCare uses [Flyway](https://flywaydb.org/) for version-controlled database schema management. Flyway is **disabled in the `dev` profile** (Hibernate manages DDL via `ddl-auto: update`) and **enabled automatically in `staging` and `prod`** profiles.

---

## Dependencies

Both required Flyway artifacts are declared in `pom.xml` and version-managed by Spring Boot's BOM:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

No explicit version is needed — Spring Boot 4.x resolves Flyway 10.x, which supports PostgreSQL 15+.

---

## Profile Behavior

| Profile | Flyway enabled | DDL managed by     |
|---------|----------------|--------------------|
| `dev`   | No             | Hibernate (`update`) |
| `staging` | Yes          | Flyway             |
| `prod`  | Yes            | Flyway             |

Key `application.yml` settings (base):

```yaml
spring:
  flyway:
    enabled: false                   # overridden to true in staging/prod
    locations: classpath:db/migration
    baseline-on-migrate: false
    validate-on-migrate: true
```

Staging and prod additionally set:

```yaml
spring:
  flyway:
    connect-retries: 3
    connect-retries-interval: 2     # seconds between retries (max 6s total wait)
```

---

## Migration Script Location and Naming

All scripts live in:

```
src/main/resources/db/migration/
```

### Naming Convention

Flyway enforces the following strict format:

```
V{version}__{description}.sql
```

| Part          | Rule                                                                 |
|---------------|----------------------------------------------------------------------|
| `V`           | Uppercase prefix — mandatory for versioned migrations                |
| `{version}`   | Integer or dotted integer (e.g. `1`, `2`, `1.1`) — must be unique  |
| `__`          | **Double** underscore separator — required                           |
| `{description}` | Snake_case words describing the change                             |
| `.sql`        | Lowercase extension                                                  |

**Valid examples:**

```
V1__init_schema.sql
V2__add_patient_contact_info.sql
V3__create_evaluation_audit_log.sql
V1.1__add_index_evaluations_status.sql
```

**Invalid examples (will fail on startup):**

```
V1_init_schema.sql        ← single underscore
v1__init_schema.sql       ← lowercase v
V1__Init Schema.sql       ← spaces
V01__init_schema.sql      ← leading zero (treated as different version)
```

> Flyway rejects any script whose checksum changes after it has been applied.
> **Never modify a migration file that has already been executed.** Add a new versioned script instead.

---

## Version Tracking — `flyway_schema_history`

Flyway automatically creates and maintains the `flyway_schema_history` table in the target database. This table is the source of truth for which migrations have been applied.

### Schema

| Column              | Type        | Description                                      |
|---------------------|-------------|--------------------------------------------------|
| `installed_rank`    | integer     | Execution order                                  |
| `version`           | varchar(50) | Script version number (e.g. `1`)                 |
| `description`       | varchar(200)| Script description (e.g. `init schema`)          |
| `type`              | varchar(20) | Always `SQL` for file-based migrations           |
| `script`            | varchar(1000)| Script filename                                  |
| `checksum`          | integer     | CRC32 of the script content — mismatch = failure |
| `installed_by`      | varchar(100)| DB user that ran the migration                   |
| `installed_on`      | timestamp   | Wall-clock time of execution                     |
| `execution_time`    | integer     | Duration in milliseconds                         |
| `success`           | boolean     | `true` if migration completed without error      |

### Verify Migration Status

Connect to the running PostgreSQL container:

```bash
docker exec -it pneumacare-postgres psql -U $DB_USER -d $DB_NAME
```

Check all applied migrations:

```sql
SELECT version, description, script, execution_time, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

Expected output after a clean first startup (AC3):

```
 version | description | script              | execution_time | success | installed_on
---------+-------------+---------------------+----------------+---------+-----------------------------
 1       | init schema | V1__init_schema.sql |    <ms>        | t       | 2025-xx-xx xx:xx:xx.xxxxxx
```

Check migration status via Flyway Maven plugin (without starting the app):

```bash
./mvnw flyway:info -Dflyway.url=jdbc:postgresql://localhost:5432/$DB_NAME \
                   -Dflyway.user=$DB_USER \
                   -Dflyway.password=$DB_PASSWORD
```

---

## Database Schema Architecture

The full schema is defined in `V1__init_schema.sql` and comprises 26 tables across 9 logical groups.

### Table Map

| Group | Tables |
|---|---|
| Geographic / Org hierarchy | `provinces`, `hospitals`, `intensive_care_units` |
| Users & RBAC | `users`, `roles`, `user_roles` |
| ICU Infrastructure | `ventilator_models`, `physical_ventilators`, `icu_beds` |
| Patient Identity (PII) | `patient_identities` |
| Patient Operations | `patients`, `patient_consents` |
| Shift Management | `medical_shifts`, `shift_handovers`, `clinical_assignments` |
| Clinical Records | `evaluations`, `spontaneous_breathing_trials`, `airway_assessments`, `airway_events`, `arterial_blood_gases` |
| Alerts & AI | `clinical_alerts_log`, `ai_clinical_insights` |
| Audit (Envers) | `audit_revisions`, `patient_identities_aud`, `patients_aud`, `evaluations_aud` |

### PII / Clinical Data Separation

PII is strictly isolated in a dedicated table, **separate from the operational patient record**:

| Table                | Data class  | Contains PII | Purpose                                      |
|----------------------|-------------|--------------|----------------------------------------------|
| `patient_identities` | **PII**     | **Yes**      | Identity only: names, national ID, birth date |
| `patients`           | Operational | No           | Clinical status, bed assignment, ICU link    |
| `evaluations`        | Clinical    | No           | Respiratory measurements and computed indices |

`patients.identity_id` is the **only FK** linking operational data to PII. All other clinical tables (`evaluations`, `airway_assessments`, etc.) reference `patients.id` — never `patient_identities` directly.

### PII Fields — Encryption Roadmap

The following `patient_identities` columns are flagged with `[PII]` comments in the SQL and are candidates for AES-256 column-level encryption via a JPA `AttributeConverter`:

| Column        | Sensitivity |
|---------------|-------------|
| `first_name`  | High        |
| `last_name`   | High        |
| `national_id` | High        |
| `birth_date`  | Medium      |

When the encryption filter is implemented:
1. Add a JPA `@Converter` that encrypts on `convertToDatabaseColumn` and decrypts on `convertToEntityAttribute`.
2. The PostgreSQL column type stays `VARCHAR` — the encrypted value is Base64-encoded ciphertext.
3. A follow-up Flyway migration will handle the data backfill for any existing rows.

### Referential Integrity

```
provinces (1)──(N) hospitals (1)──(N) intensive_care_units (1)──(N) icu_beds
                                             │                    └──(N) physical_ventilators
                                             │                    └──(N) medical_shifts ──(1) shift_handovers
                                             │                               └──(N) clinical_assignments
                                             │
                              patient_identities [PII] (1)──(1) patients
                                                                    │
                                          ┌─────────────────────────┼──────────────────────────────┐
                                          │                          │                              │
                               (N) evaluations             (N) spontaneous_breathing_trials   (N) airway_assessments
                                    │    └── physical_ventilators    │                        (N) airway_events
                                    │    └── medical_shifts (shift)  └── medical_shifts       (N) arterial_blood_gases
                                    │                                                         (N) patient_consents
                             (N) clinical_alerts_log
                             (N) ai_clinical_insights

audit_revisions (1)──(N) patient_identities_aud
               (1)──(N) patients_aud
               (1)──(N) evaluations_aud
```

- An `evaluations` record **must** reference a valid `patients` row (`patient_id`). Inserting an evaluation without a valid `patient_id` raises a foreign key constraint violation (BDD Scenario 2).
- Deleting an `icu_beds` row sets `patients.bed_id` to NULL (`ON DELETE SET NULL`) — the patient is not deleted.

### Audit Tables (Hibernate Envers) — Planned

> **Status: planned — not yet active.**
> `hibernate-envers` is not yet declared in `pom.xml` and no `@Audited` entities exist.
> The `*_aud` tables are pre-created in `V1__init_schema.sql` so that `ddl-auto: validate`
> in staging/prod does not fail once Envers is added. No audit writes occur until the
> dependency and entity annotations are in place.

Three shadow tables are reserved to track the full change history of sensitive entities once Envers is enabled:

| Shadow table             | Will track changes to   |
|--------------------------|-------------------------|
| `patient_identities_aud` | `patient_identities`    |
| `patients_aud`           | `patients`              |
| `evaluations_aud`        | `evaluations`           |

`audit_revisions` is reserved as the custom `@RevisionEntity` table. Each `*_aud` row will have a composite PK of `(id, rev)` and `revtype` (0=INSERT, 1=UPDATE, 2=DELETE).

---

## Performance Constraint

> Migration execution must not block Spring Boot startup for more than **2000 ms**.

`V1__init_schema.sql` consists entirely of DDL statements (`CREATE TABLE`, `CREATE INDEX`) and executes well under 100 ms on a local PostgreSQL instance. Monitor actual execution time in `flyway_schema_history.execution_time`.

For future migrations: if a migration requires a data backfill on a large table, split it into:
1. A DDL-only script (fast, runs inline at startup).
2. A separate background job or a second migration that runs `CONCURRENTLY` for index creation.

---

## Environment Variables

All database connection parameters are supplied through environment variables — no credentials are hardcoded in source.

| Variable      | Used in                        | Example value              |
|---------------|--------------------------------|----------------------------|
| `DB_HOST`     | `application.yml` datasource   | `localhost` / `postgres`   |
| `DB_PORT`     | `application.yml` datasource   | `5432`                     |
| `DB_NAME`     | `application.yml` datasource   | `pneumacare_db`            |
| `DB_USER`     | `application.yml` datasource   | `pneumacare_app`           |
| `DB_PASSWORD` | `application.yml` datasource   | *(set in `.env`, never committed)* |

Copy `.env.example` to `.env` and set a strong `DB_PASSWORD` before running the stack:

```bash
cp .env.example .env
# edit .env — change DB_PASSWORD and GF_SECURITY_ADMIN_PASSWORD at minimum
docker compose up
```

---

## Adding a New Migration

1. Create `src/main/resources/db/migration/V{N}__{description}.sql` where `N` is the next integer.
2. Write idempotent DDL where possible (`CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`).
3. Verify against a real database by starting the staging stack:
   ```bash
   SPRING_PROFILES_ACTIVE=staging docker compose up
   ```
   Then confirm the migration was applied:
   ```bash
   docker exec -it pneumacare-postgres psql -U $DB_USER -d $DB_NAME \
     -c "SELECT version, description, execution_time, success FROM flyway_schema_history ORDER BY installed_rank;"
   ```
   > `./mvnw verify -B` does **not** exercise Flyway migrations — the current test suite has no Flyway integration test. The command compiles and runs unit tests only.
4. Never alter or delete a migration that has already been applied in any environment.
