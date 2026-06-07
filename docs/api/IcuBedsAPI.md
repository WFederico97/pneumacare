# ICU Beds API — PneumaCare

## Description

This resource provides the current ICU bed availability list for dashboard rendering.
The endpoint is tenant-scoped: it returns only beds belonging to the ICU identified
by the authenticated user's JWT claim `icu_id`.

> **Auth:** profile-dependent.
>
> - `staging`/`prod`: OAuth2 Resource Server is active and `GET /api/**` requires
>   `SCOPE_read`.
> - `dev`: OAuth2 Resource Server is disabled by profile configuration. Endpoint access
>   is open and ICU scoping falls back to `app.security.dev-default-icu-id`.

---

## Endpoint: List ICU Beds for Current User ICU

- **URL:** `GET /api/v1/icu-beds`
- **Required permissions:** authenticated JWT + `SCOPE_read` (staging/prod)
- **Request body:** none

### Behavior

1. Reads `icu_id` from the authenticated JWT (staging/prod).
   In `dev`, uses `app.security.dev-default-icu-id` fallback.
2. Returns only beds linked to that ICU.
3. Returns only dashboard statuses: `AVAILABLE` and `OCCUPIED`.
4. Sorts by `bedNumber` ascending.

### Response — 200 OK

```json
{
  "status": 200,
  "message": "Camas de UCI recuperadas exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": [
    {
      "bedNumber": "BED-001",
      "status": "AVAILABLE"
    },
    {
      "bedNumber": "BED-002",
      "status": "OCCUPIED"
    }
  ]
}
```

### Response — 200 OK (no beds yet)

```json
{
  "status": 200,
  "message": "Camas de UCI recuperadas exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": []
}
```

### Response Schema — `IcuBedResponse`

| Field       | Type     | Description                                                        |
|-------------|----------|--------------------------------------------------------------------|
| `bedNumber` | `String` | Human-readable bed identifier (e.g. `BED-001`).                   |
| `status`    | `String` | Bed state for dashboard. Possible values: `AVAILABLE`, `OCCUPIED` |

### Error Responses

| HTTP Status        | Condition                                                                 |
|--------------------|---------------------------------------------------------------------------|
| `400 Bad Request`  | JWT is authenticated but claim `icu_id` is missing or not a valid UUID.   |
| `401 Unauthorized` | Missing/invalid authentication token (staging/prod).                      |

---

## Common Response Envelope

All endpoints wrap payloads with `ApiResponseBase<T>`:

```json
{
  "status": 200,
  "message": "Result message",
  "traceId": "trace-id-for-log-correlation",
  "data": []
}
```
