# Medical Shifts API — PneumaCare

## Description

This resource manages the lifecycle of **medical shifts** — the Chief of Guard's
active duty period for an ICU. Clinical evaluations are tied to the active (`OPEN`)
shift of their ICU.

A shift's `status` has exactly two values: `OPEN` and `CLOSED`. A newly created shift
is `OPEN`; closing it records its `end_time`. **At most one `OPEN` shift may exist per
ICU** — enforced both at the application layer and by a PostgreSQL partial unique index
(`uq_medical_shifts_one_open_per_icu`) so the rule holds under concurrent requests.
Shifts in different ICUs are independent.

Server-derived fields (`chief_user_id`, `status`, `start_time`, `end_time`) are set
server-side and never accepted from the client. Timestamps are UTC, ISO-8601.

> **User story:** PNMC-91 (Epic PNMC-83 — Medical Shifts & Handovers).
> Full reference: Confluence → *API Resources → Medical Shifts API*.

> **Auth:** profile-dependent.
>
> - Role enforcement (`ROLE_CHIEF_OF_GUARD`, → `401`/`403`) is **deferred** to the
>   authentication/login user stories and is not implemented in this release.
> - `dev`: OAuth2 Resource Server is disabled by profile configuration; endpoint access
>   is open. The current user falls back to `app.security.dev-default-chief-user-id`.
> - The current user is resolved behind a `CurrentUserPort`, so auth can be added later
>   without changing the service or controller.

---

## Endpoint: Open Shift

- **URL:** `POST /api/v1/shifts`
- **Required permissions:** `ROLE_CHIEF_OF_GUARD` (deferred — open in dev)

### Request Body

| Field   | Type   | Required | Constraints                  | Description                              |
|---------|--------|----------|------------------------------|------------------------------------------|
| `icuId` | `UUID` | Yes      | Must be an existing ICU UUID | UUID of the ICU to open a shift for.     |

```json
{
  "icuId": "cccccccc-0000-0000-0000-000000000001"
}
```

### Behavior

1. Validates that the referenced ICU exists.
2. Rejects the request if the ICU already has an `OPEN` shift.
3. Derives `chief_user_id` from the current user; sets `status = OPEN` and
   `start_time` from the server clock (UTC).

### Response — 201 Created

Includes a `Location: /api/v1/shifts/{id}` header.

```json
{
  "status": 201,
  "message": "Turno abierto exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "id": "bbbbbbbb-0000-0000-0000-000000000001",
    "icuId": "cccccccc-0000-0000-0000-000000000001",
    "startedBy": "eeeeeeee-0000-0000-0000-000000000001",
    "status": "OPEN",
    "startedAt": "2026-06-13T08:00:00Z",
    "endTime": null
  }
}
```

### Error Responses

| HTTP Status                 | Condition                                            |
|-----------------------------|------------------------------------------------------|
| `400 Bad Request`           | `icuId` is missing or malformed.                     |
| `409 Conflict`              | The ICU already has an `OPEN` shift (creates nothing).|
| `422 Unprocessable Content` | The referenced ICU does not exist (creates nothing). |

---

## Endpoint: Close Shift

- **URL:** `PATCH /api/v1/shifts/{id}/close`
- **Required permissions:** `ROLE_CHIEF_OF_GUARD` (deferred — open in dev)
- **Request body:** none

### Path Parameters

| Field | Type   | Required | Description                                   |
|-------|--------|----------|-----------------------------------------------|
| `id`  | `UUID` | Yes      | UUID of the shift to close (`medical_shifts.id`). |

### Behavior

1. Loads the shift by id (`404` if absent).
2. Rejects the request if the shift is already `CLOSED`.
3. Sets `status = CLOSED` and `end_time` from the server clock (UTC).

### Response — 200 OK

```json
{
  "status": 200,
  "message": "Turno cerrado exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "id": "bbbbbbbb-0000-0000-0000-000000000001",
    "icuId": "cccccccc-0000-0000-0000-000000000001",
    "startedBy": "eeeeeeee-0000-0000-0000-000000000001",
    "status": "CLOSED",
    "startedAt": "2026-06-13T08:00:00Z",
    "endTime": "2026-06-13T20:00:00Z"
  }
}
```

### Error Responses

| HTTP Status       | Condition                                  |
|-------------------|--------------------------------------------|
| `404 Not Found`   | No shift exists with the given id.         |
| `409 Conflict`    | The shift is already `CLOSED` (no change). |

---

## Response Schema — `ShiftResponse`

| Field       | Type                          | Nullable | Description                                                     |
|-------------|-------------------------------|----------|-----------------------------------------------------------------|
| `id`        | `UUID`                        | No       | Shift UUID (`medical_shifts.id`).                               |
| `icuId`     | `UUID`                        | No       | UUID of the ICU the shift belongs to.                          |
| `startedBy` | `UUID`                        | No       | UUID of the chief who opened the shift (`chief_user_id`).      |
| `status`    | `String`                      | No       | Lifecycle status: `OPEN` or `CLOSED`.                          |
| `startedAt` | `String` (ISO-8601 date-time) | No       | UTC timestamp when the shift was opened (`start_time`).        |
| `endTime`   | `String` (ISO-8601 date-time) | Yes      | UTC timestamp when the shift was closed; `null` while `OPEN`.  |

---

## Data Model & Migrations

- **Table:** `medical_shifts` (`id`, `icu_id`, `chief_user_id`, `start_time`,
  `end_time`, `status`).
- **Flyway `V8`** — adds audit columns `created_at` / `updated_at`.
- **Flyway `V9`** — adds the partial unique index enforcing one `OPEN` shift per ICU.
- Flyway runs only in `staging`/`prod`. In `dev` (Hibernate `ddl-auto: update`) the
  partial index is not created; the one-`OPEN`-per-ICU rule is guarded by the
  application-level check.

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
