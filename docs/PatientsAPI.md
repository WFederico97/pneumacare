# Patients API — PneumaCare

## Description

This resource manages the registration and retrieval of patient identity records within
the ICU system, including PII data such as full name, national ID, and date of birth.

> **Note:** PII fields (`firstName`, `lastName`, `nationalId`) are encrypted at rest using
> **AES-256-GCM** with a random 12-byte IV per write. Encryption and decryption are handled
> transparently by the JPA layer — callers always send and receive plain text.
> In `staging`/`prod` profiles, `POST` requires OAuth2 scope `SCOPE_write` and `GET` requires
> `SCOPE_read`. In the `dev` profile all endpoints are open (`permitAll`).

---

## Endpoint: Register Patient

- **URL:** `POST /api/v1/patients`
- **Required permissions:** `SCOPE_write` (staging/prod) — open in dev

### Request Body

JSON object with the patient's identity data. All fields are plain text; the persistence
layer encrypts PII transparently.

| Field        | Type                    | Required | Description                                                                                                                                      |
|--------------|-------------------------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `firstName`  | `String`                | Yes      | Patient first name. Max 100 characters. **Stored encrypted (AES-256-GCM).**                                                                      |
| `lastName`   | `String`                | Yes      | Patient last name. Max 100 characters. **Stored encrypted (AES-256-GCM).**                                                                       |
| `nationalId` | `String`                | Yes      | National identity document number. Max 20 characters. **Stored encrypted (AES-256-GCM).** DB-level UNIQUE constraint absent due to non-deterministic encryption. |
| `birthDate`  | `String` (ISO-8601 date)| Yes      | Patient date of birth in `YYYY-MM-DD` format. Must be a past date.                                                                               |

### Request Example

```json
{
  "firstName": "Juan",
  "lastName": "Pérez",
  "nationalId": "35123456",
  "birthDate": "1989-05-14"
}
```

### Response — 201 Created

```json
{
  "status": 201,
  "message": "Patient registered successfully",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "firstName": "Juan",
    "lastName": "Pérez",
    "nationalId": "35123456",
    "birthDate": "1989-05-14"
  }
}
```

### Error Responses

| HTTP Status       | Condition                                                                                                      |
|-------------------|----------------------------------------------------------------------------------------------------------------|
| `400 Bad Request` | One or more required fields are blank, null, exceed max length, or `birthDate` is not a past date.            |

---

## Endpoint: Get Patient by ID

- **URL:** `GET /api/v1/patients/{id}`
- **Required permissions:** `SCOPE_read` (staging/prod) — open in dev

### Path Parameters

| Parameter | Type   | Required | Description                                                                                   |
|-----------|--------|----------|-----------------------------------------------------------------------------------------------|
| `id`      | `UUID` | Yes      | The unique identifier of the patient identity record, as returned by `POST /api/v1/patients`. |

### Response — 200 OK

```json
{
  "status": 200,
  "message": "Patient retrieved successfully",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "firstName": "Juan",
    "lastName": "Pérez",
    "nationalId": "35123456",
    "birthDate": "1989-05-14"
  }
}
```

### Error Responses

| HTTP Status    | Condition                                                         |
|----------------|-------------------------------------------------------------------|
| `404 Not Found`| No patient identity record exists for the given UUID.             |
