# Patients API — PneumaCare

## Description

This resource manages the registration and retrieval of patient identity records within
the ICU system, including PII data such as full name, date of birth, and structured
identifiers (DNI, CUIL, CUIT, Passport, etc.).

> **PII & encryption:** `firstName`, `lastName`, and each identifier `value` are encrypted
> at rest using **AES-256-GCM** with a random 12-byte IV per write. Encryption and decryption
> are handled transparently by the JPA layer — callers always send and receive plain text.
>
> **Auth:** In `staging`/`prod` profiles, `POST /api/v1/patients` requires OAuth2 scope
> `SCOPE_write` and `GET /api/v1/patients/{id}` requires `SCOPE_read`.
> In the `dev` profile all endpoints are open (`permitAll`).
> `GET /api/v1/identifier-types` requires no scope in any profile.

---

## Endpoint: List Identifier Types

Fetch this catalog **before** registering a patient to obtain valid `identifierTypeId` values.

- **URL:** `GET /api/v1/identifier-types`
- **Required permissions:** none

### Response — 200 OK

```json
{
  "status": 200,
  "message": "Identifier types retrieved successfully",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": [
    { "id": 1, "name": "DNI",       "description": "Documento Nacional de Identidad" },
    { "id": 2, "name": "CUIL",      "description": "Código Único de Identificación Laboral" },
    { "id": 3, "name": "CUIT",      "description": "Código Único de Identificación Tributaria" },
    { "id": 4, "name": "LE",        "description": "Libreta de Enrolamiento" },
    { "id": 5, "name": "LC",        "description": "Libreta Cívica" },
    { "id": 6, "name": "Pasaporte", "description": "Pasaporte" }
  ]
}
```

### Response Schema — `IdentifierTypeResponse`

| Field         | Type      | Description                                                                       |
|---------------|-----------|-----------------------------------------------------------------------------------|
| `id`          | `Integer` | Numeric primary key. Use as `identifierTypeId` in `POST /api/v1/patients`.        |
| `name`        | `String`  | Short code shown in the UI (e.g. `DNI`, `CUIL`).                                  |
| `description` | `String`  | Human-readable description (e.g. `Documento Nacional de Identidad`).              |

---

## Endpoint: Register Patient

- **URL:** `POST /api/v1/patients`
- **Required permissions:** `SCOPE_write` (staging/prod) — open in dev

### Request Body

JSON object with the patient's identity data. All fields are plain text; the persistence
layer encrypts PII fields transparently.

| Field         | Type                       | Required | Constraints                         | Description                                                                                             |
|---------------|----------------------------|----------|-------------------------------------|---------------------------------------------------------------------------------------------------------|
| `firstName`   | `String`                   | Yes      | Max 100 chars, not blank            | Patient first name. **Stored AES-256-GCM encrypted.**                                                   |
| `lastName`    | `String`                   | Yes      | Max 100 chars, not blank            | Patient last name. **Stored AES-256-GCM encrypted.**                                                    |
| `birthDate`   | `String` (ISO-8601 date)   | Yes      | Past date, format `YYYY-MM-DD`      | Patient date of birth.                                                                                  |
| `identifiers` | `Array<PatientIdentifierRequest>` | Yes | Min 1 element                | At least one identifier required. Each value **stored AES-256-GCM encrypted.** See nested schema below. |

#### Nested Schema — `PatientIdentifierRequest`

| Field               | Type      | Required | Constraints              | Description                                                                               |
|---------------------|-----------|----------|--------------------------|-------------------------------------------------------------------------------------------|
| `identifierTypeId`  | `Integer` | Yes      | Must exist in catalog    | ID of the identifier type. Obtain from `GET /api/v1/identifier-types`.                    |
| `value`             | `String`  | Yes      | Max 50 chars, not blank  | Raw identifier value (e.g. `35123456` for DNI). **Stored AES-256-GCM encrypted.**         |

### Request Example

```json
{
  "firstName": "Juan",
  "lastName": "Pérez",
  "birthDate": "1989-05-14",
  "identifiers": [
    { "identifierTypeId": 1, "value": "35123456" }
  ]
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
    "birthDate": "1989-05-14",
    "identifiers": [
      { "typeName": "DNI", "value": "35123456" }
    ]
  }
}
```

### Error Responses

| HTTP Status         | Condition                                                                                                                            |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `400 Bad Request`   | Validation failure: blank name, future `birthDate`, empty `identifiers` list, value exceeds max length, or unknown `identifierTypeId`. |
| `401 Unauthorized`  | Missing or invalid Bearer token with `SCOPE_write` (staging/prod only).                                                             |

---

## Endpoint: Get Patient by ID

- **URL:** `GET /api/v1/patients/{id}`
- **Required permissions:** `SCOPE_read` (staging/prod) — open in dev

### Path Parameters

| Parameter | Type   | Required | Description                                                                                   |
|-----------|--------|----------|-----------------------------------------------------------------------------------------------|
| `id`      | `UUID` | Yes      | UUID of the patient identity record, as returned by `POST /api/v1/patients`.                  |

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
    "birthDate": "1989-05-14",
    "identifiers": [
      { "typeName": "DNI", "value": "35123456" }
    ]
  }
}
```

### Response Schema — `PatientResponse`

| Field         | Type                              | Description                                                                               |
|---------------|-----------------------------------|-------------------------------------------------------------------------------------------|
| `id`          | `UUID`                            | Unique identifier of the patient identity record.                                         |
| `firstName`   | `String`                          | Patient first name, decrypted from AES-256-GCM storage.                                   |
| `lastName`    | `String`                          | Patient last name, decrypted from AES-256-GCM storage.                                    |
| `birthDate`   | `String` (ISO-8601 date)          | Patient date of birth.                                                                    |
| `identifiers` | `Array<PatientIdentifierResponse>`| List of patient identifiers. Each `value` is decrypted from AES-256-GCM storage.          |

#### Nested Schema — `PatientIdentifierResponse`

| Field      | Type     | Description                                                                      |
|------------|----------|----------------------------------------------------------------------------------|
| `typeName` | `String` | Short name of the identifier type (e.g. `DNI`, `CUIL`, `Pasaporte`).            |
| `value`    | `String` | Raw identifier value, decrypted from AES-256-GCM storage.                       |

### Error Responses

| HTTP Status         | Condition                                                          |
|---------------------|--------------------------------------------------------------------|
| `401 Unauthorized`  | Missing or invalid Bearer token with `SCOPE_read` (staging/prod only). |
| `404 Not Found`     | No patient identity record exists for the given UUID.              |

---

## Common Response Envelope

All endpoints wrap their payload in `ApiResponseBase<T>`:

```json
{
  "status":  "<HTTP status code as integer>",
  "message": "<human-readable result message>",
  "traceId": "<OpenTelemetry trace ID for log correlation, may be null>",
  "data":    "<endpoint-specific payload>"
}
```
