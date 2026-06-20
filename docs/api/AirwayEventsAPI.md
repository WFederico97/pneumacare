# Airway Events API — PneumaCare

## Description

This resource records **airway events** for a patient — intubation, extubation and
tracheostomy — and drives the patient's **respiratory status** through a strict state
machine. Each event is attached server-side to the ICU's currently `OPEN` medical
shift.

A patient's `respiratory_status` is one of `SPONTANEOUS`, `INTUBATED`,
`TRACHEOSTOMY`. It is **orthogonal** to the admission `clinical_status`
(`ADMITTED` / `DISCHARGED` / `TRANSFERRED`) and is stored in a separate column.

The allowed transitions are:

| Event          | Requires current | Resulting status |
|----------------|------------------|------------------|
| `INTUBATION`   | `SPONTANEOUS`    | `INTUBATED`      |
| `EXTUBATION`   | `INTUBATED`      | `SPONTANEOUS`    |
| `TRACHEOSTOMY` | `INTUBATED`      | `TRACHEOSTOMY`   |

Any other transition (e.g. intubating an already-intubated patient, extubating a
spontaneous one) is rejected with `409` and **nothing is written** — the event and the
patient status update happen atomically in a single transaction, or not at all.

Server-derived fields (`shiftId`, `createdBy`, `resultingStatus`) are set server-side
and never accepted from the client. Timestamps are UTC, ISO-8601.

> **User story:** PNMC-94 (Epic PNMC-83 — Medical Shifts & Handovers).
> Full reference: Confluence → *API Resources → Airway Events API*.

> **Auth:** profile-dependent.
>
> - Role enforcement (`ROLE_THERAPIST` / `ROLE_CHIEF_OF_GUARD`, → `401`/`403`) is
>   **deferred** to the authentication/login user stories and is not implemented in
>   this release.
> - `dev`: OAuth2 Resource Server is disabled by profile configuration; endpoint access
>   is open. The current user falls back to `app.security.dev-default-chief-user-id`.
> - The current user is resolved behind a `CurrentUserPort`, so auth can be added later
>   without changing the service or controller.

---

## Endpoint: Register Airway Event

- **URL:** `POST /api/v1/procedures/airway`
- **Required permissions:** `ROLE_THERAPIST` or `ROLE_CHIEF_OF_GUARD` (deferred — open in dev)

### Request Body

| Field            | Type     | Required | Constraints                                  | Description                                  |
|------------------|----------|----------|----------------------------------------------|----------------------------------------------|
| `patientId`      | `UUID`   | Yes      | Must be an existing patient UUID             | The patient the event applies to.            |
| `eventType`      | `String` | Yes      | One of `INTUBATION`, `EXTUBATION`, `TRACHEOSTOMY` | The airway event.                       |
| `eventTimestamp` | `String` | Yes      | ISO-8601 date-time                           | Clinically-reported time of the event (UTC). |

```json
{
  "patientId": "aaaaaaaa-0000-0000-0000-000000000001",
  "eventType": "INTUBATION",
  "eventTimestamp": "2026-06-13T09:30:00Z"
}
```

### Behavior

1. Validates that the referenced patient exists.
2. Resolves the `OPEN` shift for the patient's ICU; rejects if there is none.
3. Validates the transition against the patient's current `respiratory_status`.
4. Atomically persists the event (`shift_id` and `created_by` set server-side) and
   advances the patient's `respiratory_status` to the resulting value.

### Response — 201 Created

Includes a `Location: /api/v1/patients/{patientId}/airway-events` header.

```json
{
  "status": 201,
  "message": "Evento de vía aérea registrado exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "id": "ffffffff-0000-0000-0000-000000000001",
    "patientId": "aaaaaaaa-0000-0000-0000-000000000001",
    "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
    "eventType": "INTUBATION",
    "resultingStatus": "INTUBATED",
    "eventTimestamp": "2026-06-13T09:30:00Z",
    "createdBy": "eeeeeeee-0000-0000-0000-000000000001",
    "createdAt": "2026-06-13T09:30:05Z"
  }
}
```

### Error Responses

| HTTP Status       | Condition                                                                 |
|-------------------|---------------------------------------------------------------------------|
| `400 Bad Request` | Body is missing fields, or `eventType`/`eventTimestamp` is malformed.     |
| `404 Not Found`   | The referenced patient does not exist.                                     |
| `409 Conflict`    | No `OPEN` shift for the patient's ICU, **or** an illegal airway transition. Nothing is written. |

> **Note on validation status:** bean-validation and malformed-body errors return `400`,
> following the project-wide `GlobalExceptionHandler` convention (the same convention
> used by the Medical Shifts API). `422` is reserved for well-formed but unprocessable
> references elsewhere in the system.

---

## Endpoint: List Patient Airway Events

- **URL:** `GET /api/v1/patients/{id}/airway-events`
- **Required permissions:** `ROLE_THERAPIST` or `ROLE_CHIEF_OF_GUARD` (deferred — open in dev)
- **Request body:** none

### Path Parameters

| Field | Type   | Required | Description                          |
|-------|--------|----------|--------------------------------------|
| `id`  | `UUID` | Yes      | UUID of the patient (`patients.id`). |

### Behavior

1. Validates that the patient exists (`404` otherwise).
2. Returns the patient's airway events ordered by `eventTimestamp` descending
   (newest first). Each event's `resultingStatus` is derived from its `eventType`.

### Response — 200 OK

```json
{
  "status": 200,
  "message": "Eventos de vía aérea recuperados exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": [
    {
      "id": "ffffffff-0000-0000-0000-000000000002",
      "patientId": "aaaaaaaa-0000-0000-0000-000000000001",
      "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
      "eventType": "EXTUBATION",
      "resultingStatus": "SPONTANEOUS",
      "eventTimestamp": "2026-06-13T18:00:00Z",
      "createdBy": "eeeeeeee-0000-0000-0000-000000000001",
      "createdAt": "2026-06-13T18:00:04Z"
    },
    {
      "id": "ffffffff-0000-0000-0000-000000000001",
      "patientId": "aaaaaaaa-0000-0000-0000-000000000001",
      "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
      "eventType": "INTUBATION",
      "resultingStatus": "INTUBATED",
      "eventTimestamp": "2026-06-13T09:30:00Z",
      "createdBy": "eeeeeeee-0000-0000-0000-000000000001",
      "createdAt": "2026-06-13T09:30:05Z"
    }
  ]
}
```

### Error Responses

| HTTP Status     | Condition                            |
|-----------------|--------------------------------------|
| `404 Not Found` | The referenced patient does not exist. |

---

## Response Schema — `AirwayEventResponse`

| Field             | Type                          | Nullable | Description                                                          |
|-------------------|-------------------------------|----------|---------------------------------------------------------------------|
| `id`              | `UUID`                        | No       | Airway event UUID (`airway_events.id`).                            |
| `patientId`       | `UUID`                        | No       | UUID of the patient.                                               |
| `shiftId`         | `UUID`                        | No       | UUID of the `OPEN` shift the event was registered under.          |
| `eventType`       | `String`                      | No       | `INTUBATION`, `EXTUBATION` or `TRACHEOSTOMY`.                     |
| `resultingStatus` | `String`                      | No       | Patient respiratory status after this event. Derived from `eventType`. |
| `eventTimestamp`  | `String` (ISO-8601 date-time) | No       | Clinically-reported event time (`event_time`).                    |
| `createdBy`       | `UUID`                        | No       | UUID of the user who registered the event.                        |
| `createdAt`       | `String` (ISO-8601 date-time) | No       | UTC timestamp when the row was persisted.                         |

> **Design choice:** `resultingStatus` is **derived** from the event type (a pure
> function), not stored on the row. This keeps it correct both for the just-registered
> event and for every historical event in the listing, with no denormalized column.

---

## Data Model & Migrations

- **Table:** `airway_events` (`id`, `patient_id`, `shift_id`, `event_time`,
  `event_type`, `created_by`; existing in V1 — extra `tube_size` / `is_successful` /
  `complications_noted` columns are out of scope for this release).
- **`patients.respiratory_status`** — new column (`SPONTANEOUS` default), orthogonal to
  `clinical_status`.
- **Flyway `V10`** — adds `patients.respiratory_status`.
- **Flyway `V11`** — adds audit columns `created_at` / `updated_at` to `airway_events`.
- Flyway runs only in `staging`/`prod`. In `dev` (Hibernate `ddl-auto: update`) the
  columns are created from the entities.

---

## Architecture Notes

The `procedures` bounded context owns this resource. It reaches the patient, shift and
user aggregates only through ports — `PatientAirwayPort`, `ActiveShiftPort`,
`CurrentUserPort` — so the application layer imports no foreign JPA types. The airway
state machine lives entirely on the `AirwayEventType` domain enum.

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
