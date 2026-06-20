# Shift Audit & Observability API — PneumaCare

## Description

This resource exposes the **Envers revision history** for shift-context records and
documents the **observability** surface added for the shift module. Every create/update
to a `medical_shifts` or `shift_handovers` row is captured by Hibernate Envers with the
acting user and a timestamp, and writes that target a record under a `CLOSED` shift raise
a non-blocking audit alert.

Timestamps are UTC, ISO-8601.

> **User story:** PNMC-134 (Epic PNMC-83 — Medical Shifts & Handovers). Depends on
> PNMC-91 (shift lifecycle); relates to PNMC-92 (handovers).

> **Auth:** the audit endpoints require the `SCOPE_audit` authority (compliance role),
> enforced via `@PreAuthorize`.
>
> - Anonymous → `401`; authenticated without `SCOPE_audit` → `403`.
> - In `staging`/`prod` these `GET` endpoints also sit behind the `SCOPE_read` request
>   matcher, so a compliance principal needs **both** `SCOPE_read` and `SCOPE_audit`.
> - `dev`: OAuth2 is disabled by profile, but `@PreAuthorize` still applies, so the audit
>   endpoints require a principal carrying `SCOPE_audit`.

---

## AC1 — Envers revision history

Both `MedicalShiftJpaEntity` and `ShiftHandoverJpaEntity` are `@Audited`. Revisions are
written to `medical_shifts_aud` / `shift_handovers_aud`, with revision metadata in a
custom `revinfo` table whose `actor_id` column records the acting user (resolved from the
JWT `sub` claim; nil UUID in dev).

## AC2 — Retroactive-edit alert

A Hibernate `PostInsert`/`PostUpdate` listener (`ClosedShiftAuditListener`) detects:

- an **update to a `medical_shifts` row whose prior status was already `CLOSED`**, and
- an **insert/update of a `shift_handovers` row under a `CLOSED` shift**.

On detection it emits a WARN log (no PII — entity kind + UUID only) and increments the
`shift.audit.closed_shift_write_total` counter (tag `entity=medical_shift|handover`).
The write is **not** blocked; the Envers revision created in the same flush records it.

## AC3 — Metrics & traces

Application-service methods are annotated `@Observed`, producing timers/spans:

| Observation       | Method                          |
|-------------------|---------------------------------|
| `shift.open`      | `MedicalShiftService.open`      |
| `shift.close`     | `MedicalShiftService.close`     |
| `handover.create` | `ShiftHandoverService.create`   |

Span attributes are non-PII only (shift / ICU UUIDs); note content is never recorded on a
span. Per-endpoint request count, latency and error rate come from the auto-generated
`http.server.requests` metric; HTTP server spans are created by Spring MVC.

---

## Endpoint: Get Shift Audit History

- **URL:** `GET /api/v1/shifts/{id}/audit`
- **Required permissions:** `SCOPE_audit`

### Path Parameters

| Field | Type   | Required | Description                              |
|-------|--------|----------|------------------------------------------|
| `id`  | `UUID` | Yes      | UUID of the shift (`medical_shifts.id`). |

### Response — 200 OK

```json
{
  "status": 200,
  "message": "Historial de auditoría del turno recuperado exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": [
    {
      "revisionNumber": 41,
      "revisionType": "CREATE",
      "actorId": "eeeeeeee-0000-0000-0000-000000000001",
      "revisionTimestamp": "2026-06-13T08:00:00Z",
      "entity": {
        "id": "bbbbbbbb-0000-0000-0000-000000000001",
        "icuId": "cccccccc-0000-0000-0000-000000000001",
        "startedBy": "eeeeeeee-0000-0000-0000-000000000001",
        "status": "OPEN",
        "startedAt": "2026-06-13T08:00:00Z",
        "endTime": null
      }
    },
    {
      "revisionNumber": 42,
      "revisionType": "UPDATE",
      "actorId": "eeeeeeee-0000-0000-0000-000000000001",
      "revisionTimestamp": "2026-06-13T20:00:00Z",
      "entity": {
        "id": "bbbbbbbb-0000-0000-0000-000000000001",
        "icuId": "cccccccc-0000-0000-0000-000000000001",
        "startedBy": "eeeeeeee-0000-0000-0000-000000000001",
        "status": "CLOSED",
        "startedAt": "2026-06-13T08:00:00Z",
        "endTime": "2026-06-13T20:00:00Z"
      }
    }
  ]
}
```

### Error Responses

| HTTP Status        | Condition                                            |
|--------------------|------------------------------------------------------|
| `401 Unauthorized` | Anonymous caller.                                    |
| `403 Forbidden`    | Authenticated caller lacking `SCOPE_audit`.          |

---

## Endpoint: Get Handover Audit History

- **URL:** `GET /api/v1/shifts/handovers/{id}/audit`
- **Required permissions:** `SCOPE_audit`

### Path Parameters

| Field | Type   | Required | Description                                  |
|-------|--------|----------|----------------------------------------------|
| `id`  | `UUID` | Yes      | UUID of the handover (`shift_handovers.id`). |

### Response — 200 OK

```json
{
  "status": 200,
  "message": "Historial de auditoría de la nota de relevo recuperado exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": [
    {
      "revisionNumber": 50,
      "revisionType": "CREATE",
      "actorId": "eeeeeeee-0000-0000-0000-000000000001",
      "revisionTimestamp": "2026-06-13T19:45:00Z",
      "entity": {
        "id": "ffffffff-0000-0000-0000-000000000001",
        "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
        "authorId": "eeeeeeee-0000-0000-0000-000000000001",
        "notesContent": "Cama 3 estable, destete en curso.",
        "createdAt": "2026-06-13T19:45:00Z"
      }
    }
  ]
}
```

### Error Responses

| HTTP Status        | Condition                                   |
|--------------------|---------------------------------------------|
| `401 Unauthorized` | Anonymous caller.                           |
| `403 Forbidden`    | Authenticated caller lacking `SCOPE_audit`. |

---

## Response Schema — `AuditRevisionResponse<T>`

| Field               | Type                          | Nullable | Description                                                   |
|---------------------|-------------------------------|----------|---------------------------------------------------------------|
| `revisionNumber`    | `Number`                      | No       | Envers revision number (monotonically increasing).            |
| `revisionType`      | `String`                      | No       | `CREATE`, `UPDATE` or `DELETE`.                               |
| `actorId`           | `UUID`                        | No       | User who made the change; nil UUID when unauthenticated (dev).|
| `revisionTimestamp` | `String` (ISO-8601 date-time) | No       | UTC timestamp of the revision.                                |
| `entity`            | `ShiftResponse`/`HandoverResponse` | Yes | Record snapshot at this revision; `null` for a `DELETE`.     |

---

## Data Model & Migrations

- **Tables (new):** `revinfo` (revision metadata + `actor_id`), `medical_shifts_aud`,
  `shift_handovers_aud`, plus the `revinfo_seq` sequence.
- **Flyway `V14`** — creates the Envers schema for `staging`/`prod` (`ddl=validate`). In
  `dev` (Hibernate `ddl-auto: update`) these tables are created automatically.
- Existing rows are **not** back-filled — audit history starts at the first write after the
  migration is applied.

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
