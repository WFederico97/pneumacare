# Patients API — PneumaCare

## Description

This resource manages the registration and retrieval of patient identity records within
the ICU system, including PII data such as full name, date of birth, and structured
identifiers (DNI, CUIL, CUIT, Passport, etc.).

> **PII & encryption:** `firstName`, `lastName`, `dni`, and each additional identifier
> `value` are encrypted at rest using **AES-256-GCM** with a random 12-byte IV per write.
> Encryption and decryption are handled transparently by the JPA layer — callers always
> send and receive plain text.
>
> **Auth:** In `staging`/`prod` profiles, `POST /api/v1/patients` requires OAuth2 scope
> `SCOPE_write` and `GET /api/v1/patients/{id}` requires `SCOPE_read`.
> In the `dev` profile all endpoints are open (`permitAll`).
> `GET /api/v1/identifier-types` requires no scope in any profile.

---

## Endpoint: List Identifier Types

Fetch this catalog **before** registering a patient to obtain valid `identifierTypeId`
values for the `additionalIdentifiers` array.

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

| Field         | Type      | Description                                                                  |
|---------------|-----------|------------------------------------------------------------------------------|
| `id`          | `Integer` | Numeric primary key. Use as `identifierTypeId` in `additionalIdentifiers`.   |
| `name`        | `String`  | Short code shown in the UI (e.g. `DNI`, `CUIL`).                            |
| `description` | `String`  | Human-readable description (e.g. `Documento Nacional de Identidad`).        |

---

## Endpoint: Register Patient

Admits a patient in a single atomic transaction:

1. Validates that the ICU exists.
2. Validates that the bed belongs to that ICU and its status is `AVAILABLE`.
3. Creates the `patient_identities` PII record (name, birth date, DNI, additional identifiers). All PII values are stored **AES-256-GCM encrypted**.
4. Creates the operational `patients` record linking identity, ICU, and bed.
5. Marks the bed `OCCUPIED`.

On any failure the entire transaction is rolled back.

- **URL:** `POST /api/v1/patients`
- **Required permissions:** `SCOPE_write` (staging/prod) — open in dev

### Request Body

All fields are plain text; the persistence layer encrypts PII fields transparently.

| Field                  | Type                              | Required | Constraints                              | Description                                                                                                                                               |
|------------------------|-----------------------------------|----------|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `firstName`            | `String`                          | Yes      | Not blank, max 100 chars                 | Patient first name. **Stored AES-256-GCM encrypted.**                                                                                                     |
| `lastName`             | `String`                          | Yes      | Not blank, max 100 chars                 | Patient last name. **Stored AES-256-GCM encrypted.**                                                                                                      |
| `birthDate`            | `String` (ISO-8601 date)          | Yes      | Past date, format `YYYY-MM-DD`           | Patient date of birth.                                                                                                                                    |
| `dni`                  | `String`                          | Yes      | 7–8 numeric digits, no dots or spaces    | Argentine national identity number (DNI). **Stored AES-256-GCM encrypted.** Must not appear again in `additionalIdentifiers`.                             |
| `icuId`                | `UUID`                            | Yes      | Must be a valid ICU UUID                 | UUID of the Intensive Care Unit where the patient is being admitted. Obtain valid values from `GET /api/v1/icus`.                                         |
| `bedId`                | `UUID`                            | Yes      | Must belong to `icuId`, status AVAILABLE | UUID of the bed to assign. The bed must belong to the given ICU and have status `AVAILABLE`.                                                              |
| `additionalIdentifiers`| `Array<PatientIdentifierRequest>` | No       | Must not contain a DNI entry             | Optional list of extra identifiers (CUIL, CUIT, Pasaporte, etc.). Use `identifierTypeId` values from `GET /api/v1/identifier-types`. Omit to send none.   |

#### Nested Schema — `PatientIdentifierRequest`

| Field              | Type      | Required | Constraints             | Description                                                                        |
|--------------------|-----------|----------|-------------------------|------------------------------------------------------------------------------------|
| `identifierTypeId` | `Integer` | Yes      | Must exist in catalog   | ID of the identifier type. Obtain from `GET /api/v1/identifier-types`. Not DNI (id 1). |
| `value`            | `String`  | Yes      | Not blank, max 50 chars | Raw identifier value (e.g. `20-35123456-4` for CUIL). **Stored AES-256-GCM encrypted.** |

### Request Examples

**Minimal — DNI only:**

```json
{
  "firstName": "Juan",
  "lastName": "Pérez",
  "birthDate": "1989-05-14",
  "dni": "35123456",
  "icuId": "cccccccc-0000-0000-0000-000000000001",
  "bedId": "dddddddd-0000-0000-0000-000000000001"
}
```

**With additional identifier (CUIL):**

```json
{
  "firstName": "María",
  "lastName": "González",
  "birthDate": "1975-11-22",
  "dni": "12345678",
  "icuId": "cccccccc-0000-0000-0000-000000000001",
  "bedId": "dddddddd-0000-0000-0000-000000000002",
  "additionalIdentifiers": [
    { "identifierTypeId": 2, "value": "27-12345678-4" }
  ]
}
```

### Response — 201 Created

```json
{
  "status": 201,
  "message": "Patient admitted successfully",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "patientId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "firstName": "Juan",
    "lastName": "Pérez",
    "birthDate": "1989-05-14",
    "dni": "35123456",
    "icuId": "cccccccc-0000-0000-0000-000000000001",
    "bedId": "dddddddd-0000-0000-0000-000000000001",
    "admissionDate": "2026-06-06T10:00:00-03:00",
    "clinicalStatus": "ADMITTED",
    "additionalIdentifiers": []
  }
}
```

### Error Responses

| HTTP Status        | Condition                                                                                                                                                                                 |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `400 Bad Request`  | Validation failure: blank name, future `birthDate`, missing or malformed `dni`, missing `icuId` or `bedId`, bed not found in the given ICU, bed not `AVAILABLE`, duplicate identifier type in request, unknown `identifierTypeId`. |
| `401 Unauthorized` | Missing or invalid Bearer token with `SCOPE_write` (staging/prod only).                                                                                                                  |
| `404 Not Found`    | No ICU found for the given `icuId`.                                                                                                                                                       |

---

## Endpoint: Get Patient by ID

- **URL:** `GET /api/v1/patients/{id}`
- **Required permissions:** `SCOPE_read` (staging/prod) — open in dev

### Path Parameters

| Parameter | Type   | Required | Description                                                                                     |
|-----------|--------|----------|-------------------------------------------------------------------------------------------------|
| `id`      | `UUID` | Yes      | Operational patient UUID (`patients.id`), as returned by `POST /api/v1/patients` → `data.patientId`. |

### Response — 200 OK

```json
{
  "status": 200,
  "message": "Patient retrieved successfully",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "patientId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "firstName": "Juan",
    "lastName": "Pérez",
    "birthDate": "1989-05-14",
    "dni": "35123456",
    "icuId": "cccccccc-0000-0000-0000-000000000001",
    "bedId": "dddddddd-0000-0000-0000-000000000001",
    "admissionDate": "2026-06-06T10:00:00-03:00",
    "clinicalStatus": "ADMITTED",
    "additionalIdentifiers": []
  }
}
```

### Response Schema — `PatientResponse`

| Field                  | Type                              | Nullable | Description                                                                                   |
|------------------------|-----------------------------------|----------|-----------------------------------------------------------------------------------------------|
| `patientId`            | `UUID`                            | No       | Operational patient UUID (`patients.id`). Referenced by all clinical tables.                  |
| `firstName`            | `String`                          | No       | Patient first name, decrypted from AES-256-GCM storage.                                      |
| `lastName`             | `String`                          | No       | Patient last name, decrypted from AES-256-GCM storage.                                       |
| `birthDate`            | `String` (ISO-8601 date)          | No       | Patient date of birth (`YYYY-MM-DD`).                                                        |
| `dni`                  | `String`                          | No       | DNI number, decrypted from AES-256-GCM storage.                                              |
| `icuId`                | `UUID`                            | No       | UUID of the ICU the patient was admitted to.                                                  |
| `bedId`                | `UUID`                            | Yes      | UUID of the assigned bed. `null` if no bed was assigned.                                      |
| `admissionDate`        | `String` (ISO-8601 date-time)     | No       | Timezone-aware admission timestamp (e.g. `2026-06-06T10:00:00-03:00`).                       |
| `clinicalStatus`       | `String`                          | No       | Current clinical status (e.g. `ADMITTED`).                                                   |
| `additionalIdentifiers`| `Array<PatientIdentifierResponse>`| No       | Extra identifiers (CUIL, CUIT, etc.). Does **not** include the DNI — use the top-level `dni` field. |

#### Nested Schema — `PatientIdentifierResponse`

| Field      | Type     | Description                                                              |
|------------|----------|--------------------------------------------------------------------------|
| `typeName` | `String` | Short name of the identifier type (e.g. `CUIL`, `Pasaporte`).           |
| `value`    | `String` | Raw identifier value, decrypted from AES-256-GCM storage.               |

### Error Responses

| HTTP Status        | Condition                                                              |
|--------------------|------------------------------------------------------------------------|
| `401 Unauthorized` | Missing or invalid Bearer token with `SCOPE_read` (staging/prod only). |
| `404 Not Found`    | No admitted patient found for the given UUID.                          |

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
