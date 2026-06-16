# Shift Handovers API — PneumaCare

## Description

This resource records **handover notes** for a medical shift — free-text clinical
context the outgoing team leaves for the incoming team. A note is **immutable** once
created and strictly tied to a `shiftId`. Notes may only be added to an `OPEN` shift, and
a shift may have many notes.

The author is derived from the authenticated principal (never from the body). Timestamps
are UTC, ISO-8601.

> **User story:** PNMC-92 (Epic PNMC-83 — Medical Shifts & Handovers). Depends on
> PNMC-91 (shift lifecycle). Full reference: Confluence → *API Resources → Shift Handovers API*.

> **Auth:** profile-dependent.
>
> - Role enforcement (`ROLE_THERAPIST` / `ROLE_CHIEF_OF_GUARD`, → `401`/`403`) is
>   **deferred** to the authentication/login user stories and is not implemented in
>   this release.
> - `dev`: OAuth2 Resource Server is disabled by profile configuration; endpoint access
>   is open. The author falls back to `app.security.dev-default-chief-user-id`.
> - The author is resolved behind a shared `CurrentUserPort`, so auth can be added later
>   without changing the service or controller.

---

## Endpoint: Submit Handover Note

- **URL:** `POST /api/v1/shifts/{id}/handovers`
- **Required permissions:** `ROLE_THERAPIST` or `ROLE_CHIEF_OF_GUARD` (deferred — open in dev)

### Path Parameters

| Field | Type   | Required | Description                              |
|-------|--------|----------|------------------------------------------|
| `id`  | `UUID` | Yes      | UUID of the shift (`medical_shifts.id`). |

### Request Body

| Field          | Type     | Required | Constraints                  | Description           |
|----------------|----------|----------|------------------------------|-----------------------|
| `notesContent` | `String` | Yes      | Non-empty, max 4000 chars    | The handover note.    |

```json
{
  "notesContent": "Cama 3 estable, destete en curso. Cama 5 requiere control de sedación."
}
```

### Behavior

1. Validates that `notesContent` is non-empty and at most 4000 characters.
2. Validates that the shift exists.
3. Validates that the shift is `OPEN`.
4. Persists the note (`authorId` from the current user; `createdAt` from auditing).

### Response — 201 Created

Includes a `Location: /api/v1/shifts/{id}/handovers` header.

```json
{
  "status": 201,
  "message": "Nota de relevo registrada exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "id": "ffffffff-0000-0000-0000-000000000001",
    "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
    "authorId": "eeeeeeee-0000-0000-0000-000000000001",
    "notesContent": "Cama 3 estable, destete en curso.",
    "createdAt": "2026-06-13T19:45:00Z"
  }
}
```

### Error Responses

| HTTP Status                 | Condition                                                   |
|-----------------------------|------------------------------------------------------------|
| `404 Not Found`             | No shift exists with the given id.                         |
| `409 Conflict`              | The shift is `CLOSED` (persists nothing).                  |
| `422 Unprocessable Content` | `notesContent` is empty/missing or exceeds 4000 characters.|

> **Note on validation status:** content validation returns `422` per the ticket's AC4
> (empty/missing content → `422`), enforced in the service rather than via bean
> validation (which would yield `400`).

---

## Endpoint: List Handover Notes

- **URL:** `GET /api/v1/shifts/{id}/handovers`
- **Required permissions:** `ROLE_THERAPIST` or `ROLE_CHIEF_OF_GUARD` (deferred — open in dev)
- **Request body:** none

### Behavior

1. Validates that the shift exists (`404` otherwise).
2. Returns the shift's notes ordered by `createdAt` descending (newest first).

### Response — 200 OK

```json
{
  "status": 200,
  "message": "Notas de relevo recuperadas exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": [
    {
      "id": "ffffffff-0000-0000-0000-000000000002",
      "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
      "authorId": "eeeeeeee-0000-0000-0000-000000000001",
      "notesContent": "Actualización 20:00 — sin cambios.",
      "createdAt": "2026-06-13T20:00:00Z"
    },
    {
      "id": "ffffffff-0000-0000-0000-000000000001",
      "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
      "authorId": "eeeeeeee-0000-0000-0000-000000000001",
      "notesContent": "Cama 3 estable, destete en curso.",
      "createdAt": "2026-06-13T19:45:00Z"
    }
  ]
}
```

### Error Responses

| HTTP Status     | Condition                          |
|-----------------|------------------------------------|
| `404 Not Found` | No shift exists with the given id. |

---

## Response Schema — `HandoverResponse`

| Field          | Type                          | Nullable | Description                                       |
|----------------|-------------------------------|----------|---------------------------------------------------|
| `id`           | `UUID`                        | No       | Handover note UUID (`shift_handovers.id`).       |
| `shiftId`      | `UUID`                        | No       | UUID of the shift the note belongs to.           |
| `authorId`     | `UUID`                        | No       | UUID of the author (derived from the principal). |
| `notesContent` | `String`                      | No       | Note content.                                    |
| `createdAt`    | `String` (ISO-8601 date-time) | No       | UTC timestamp when the note was created.         |

---

## Data Model & Migrations

- **Table:** `shift_handovers` (existing in V1). This flow maps `shift_id`, plus new
  `author_id` / `notes_content` columns; `created_at` (audited) is the ticket's
  `created_at`. The legacy `incoming_notes` / `outgoing_notes` /
  `critical_events_summary` / `closed_at` columns are out of scope and left unmapped.
- **Flyway `V13`** — drops the V1 `UNIQUE(shift_id)` constraint (many notes per shift),
  adds `author_id` / `notes_content` / audit columns, and adds an index on `shift_id`.
- Flyway runs only in `staging`/`prod`. In `dev` (Hibernate `ddl-auto: update`) the
  table and columns are created from the entity (without the legacy columns or the
  unique constraint).

---

## Architecture Notes

This resource lives in the `shift` bounded context. The service reads the shift directly
via `MedicalShiftRepository` (same context) to enforce existence + `OPEN` status, and
resolves the author through the shared `CurrentUserPort`.

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
