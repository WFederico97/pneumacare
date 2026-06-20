# SBT API — PneumaCare

## Description

This resource records the results of **Spontaneous Breathing Trials (SBT)** — whether
a patient tolerated breathing on their own — and returns a patient's SBT history. Each
record is attached server-side to the ICU's currently `OPEN` medical shift.

`tolerance_result` is one of `SUCCESS` or `FAILURE` (a `FAILURE` is a valid, clinically
meaningful recorded outcome). An SBT is modelled as a **recorded result**
(`durationMinutes` + `toleranceResult`), not a time-tracked trial.

Server-derived fields (`shiftId`, `performedBy`, `recordedAt`) are set server-side and
never accepted from the client. Timestamps are UTC, ISO-8601.

> **User story:** PNMC-95 (Epic PNMC-83 — Medical Shifts & Handovers).
> Full reference: Confluence → *API Resources → SBT API*.

> **Auth:** profile-dependent.
>
> - Role enforcement (`ROLE_THERAPIST` / `ROLE_CHIEF_OF_GUARD`, → `401`/`403`) is
>   **deferred** to the authentication/login user stories and is not implemented in
>   this release.
> - `dev`: OAuth2 Resource Server is disabled by profile configuration; endpoint access
>   is open. The current user falls back to `app.security.dev-default-chief-user-id`.
> - The current user is resolved behind a shared `CurrentUserPort`, so auth can be added
>   later without changing the service or controller.

---

## Endpoint: Record SBT

- **URL:** `POST /api/v1/procedures/sbt`
- **Required permissions:** `ROLE_THERAPIST` or `ROLE_CHIEF_OF_GUARD` (deferred — open in dev)

### Request Body

| Field             | Type      | Required | Constraints                  | Description                          |
|-------------------|-----------|----------|------------------------------|--------------------------------------|
| `patientId`       | `UUID`    | Yes      | Must be an existing patient  | The patient the trial was performed on. |
| `durationMinutes` | `Integer` | Yes      | Positive integer (`> 0`)     | Trial duration in minutes.           |
| `toleranceResult` | `String`  | Yes      | `SUCCESS` or `FAILURE`       | Trial outcome.                       |

```json
{
  "patientId": "aaaaaaaa-0000-0000-0000-000000000001",
  "durationMinutes": 30,
  "toleranceResult": "SUCCESS"
}
```

### Behavior

1. Validates that `durationMinutes` is a positive integer.
2. Validates that the referenced patient exists.
3. Resolves the `OPEN` shift for the patient's ICU; rejects if there is none.
4. Persists the SBT (`shiftId` and `performedBy` set server-side; `recordedAt` from the
   audited `created_at`).

### Response — 201 Created

Includes a `Location: /api/v1/procedures/sbt?patientId={patientId}` header.

```json
{
  "status": 201,
  "message": "SBT registrado exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "id": "ffffffff-0000-0000-0000-000000000001",
    "patientId": "aaaaaaaa-0000-0000-0000-000000000001",
    "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
    "durationMinutes": 30,
    "toleranceResult": "SUCCESS",
    "performedBy": "eeeeeeee-0000-0000-0000-000000000001",
    "recordedAt": "2026-06-13T10:15:00Z"
  }
}
```

### Error Responses

| HTTP Status                 | Condition                                                        |
|-----------------------------|------------------------------------------------------------------|
| `400 Bad Request`           | Body is missing fields, or a field is malformed (e.g. non-integer duration). |
| `404 Not Found`             | The referenced patient does not exist.                           |
| `409 Conflict`              | No `OPEN` shift for the patient's ICU.                           |
| `422 Unprocessable Content` | `durationMinutes` is not a positive integer (`<= 0`).           |

> **Note on validation status:** a well-formed request with a non-positive
> `durationMinutes` returns `422` (semantic validation). A *malformed* body (missing
> field, non-integer duration) returns `400`, following the project-wide
> `GlobalExceptionHandler` convention.

---

## Endpoint: List SBT History

- **URL:** `GET /api/v1/procedures/sbt?patientId={id}`
- **Required permissions:** `ROLE_THERAPIST` or `ROLE_CHIEF_OF_GUARD` (deferred — open in dev)
- **Request body:** none

### Query Parameters

| Field       | Type   | Required | Description                          |
|-------------|--------|----------|--------------------------------------|
| `patientId` | `UUID` | Yes      | UUID of the patient (`patients.id`). |

### Behavior

1. Validates that the patient exists (`404` otherwise).
2. Returns the patient's SBT records ordered by `recordedAt` descending (newest first).

### Response — 200 OK

```json
{
  "status": 200,
  "message": "Historial de SBT recuperado exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": [
    {
      "id": "ffffffff-0000-0000-0000-000000000002",
      "patientId": "aaaaaaaa-0000-0000-0000-000000000001",
      "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
      "durationMinutes": 45,
      "toleranceResult": "FAILURE",
      "performedBy": "eeeeeeee-0000-0000-0000-000000000001",
      "recordedAt": "2026-06-13T16:40:00Z"
    },
    {
      "id": "ffffffff-0000-0000-0000-000000000001",
      "patientId": "aaaaaaaa-0000-0000-0000-000000000001",
      "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
      "durationMinutes": 30,
      "toleranceResult": "SUCCESS",
      "performedBy": "eeeeeeee-0000-0000-0000-000000000001",
      "recordedAt": "2026-06-13T10:15:00Z"
    }
  ]
}
```

### Error Responses

| HTTP Status     | Condition                              |
|-----------------|----------------------------------------|
| `404 Not Found` | The referenced patient does not exist. |

---

## Response Schema — `SbtResponse`

| Field             | Type                          | Nullable | Description                                              |
|-------------------|-------------------------------|----------|---------------------------------------------------------|
| `id`              | `UUID`                        | No       | SBT UUID (`spontaneous_breathing_trials.id`).          |
| `patientId`       | `UUID`                        | No       | UUID of the patient.                                   |
| `shiftId`         | `UUID`                        | No       | UUID of the `OPEN` shift the trial was recorded under. |
| `durationMinutes` | `Integer`                     | No       | Trial duration in minutes.                             |
| `toleranceResult` | `String`                      | No       | `SUCCESS` or `FAILURE`.                                |
| `performedBy`     | `UUID`                        | No       | UUID of the user who recorded the trial (`created_by`).|
| `recordedAt`      | `String` (ISO-8601 date-time) | No       | UTC timestamp when the trial was recorded (`created_at`). |

---

## Data Model & Migrations

- **Table:** `spontaneous_breathing_trials` (existing in V1). This flow maps
  `patient_id`, `shift_id`, `duration_minutes`, `outcome` (→ `toleranceResult`), and
  `created_by` (→ `performedBy`). The `start_time` / `end_time` / `trial_mode` /
  `failure_reason` columns are out of scope for this release and left unmapped.
- **Flyway `V12`** — adds audit columns `created_at` / `updated_at`, and drops the
  `start_time NOT NULL` constraint (this flow records a result, not a timed trial).
- Flyway runs only in `staging`/`prod`. In `dev` (Hibernate `ddl-auto: update`) the
  table and columns are created from the entity.

---

## Architecture Notes

The `procedures` bounded context owns this resource. It reaches the patient, shift and
user aggregates only through ports — `PatientLookupPort` (patient existence + ICU),
`ActiveShiftPort`, and the shared `CurrentUserPort` — so the application layer imports no
foreign JPA types.

---

## Common Response Envelope

All endpoints wrap payloads with `ApiResponseBase<T>`:

```json
{
  "status": 200,
  "message": "Result message",
  "traceId": "trace-id-for-log-correlation",
  "data": {}
}
```
