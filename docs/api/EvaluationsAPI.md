# Evaluations API — PneumaCare

## Description

This resource records a single ventilator evaluation for an admitted patient. The caller
submits raw ventilator readings; the server validates them, routes them through the
brand-specific mathematical engine, computes the three respiratory indices
(**RSBI**, **PaFi**, **Cstat**) together with their clinical interpretations, and stores
an **immutable** evaluation record.

> **Multi-ventilator engine:** the `brand` field selects a `VentilatorStrategy`
> (`TECME` or `NEUMOVENT`). Each strategy applies any brand-specific unit conversions
> before delegating to the shared `ClinicalMathEngine`. The request always carries
> `vt` in **mL** — the strategy is the only place that knows whether the hardware
> reports tidal volume in mL or litres.
>
> **Interpretation persistence:** the numeric snapshots *and* their interpretation enums
> are stored at evaluation time, so the recorded clinical judgement stays stable even if
> the classification thresholds are later revised.
>
> **Auth:** In `staging`/`prod`, `POST /api/v1/evaluations` requires role
> `ROLE_THERAPIST`. In the `dev` profile the endpoint is open (`permitAll`).
> `created_by` is taken from the JWT `sub` claim (nil UUID in dev when no token is present).

---

## Endpoint: Persist a Ventilator Evaluation

- **URL:** `POST /api/v1/evaluations`
- **Required permissions:** `ROLE_THERAPIST` (staging/prod) — open in dev

### Calculation conventions

| Index   | Formula                          | Unit              |
|---------|----------------------------------|-------------------|
| `RSBI`  | `f / (Vt[mL] / 1000)`            | breaths·min⁻¹·L⁻¹ |
| `PaFi`  | `PaO₂ / FiO₂`                    | mmHg              |
| `Cstat` | `Vt[mL] / (Pplat − PEEP)`        | mL/cmH₂O          |

### Request Body

| Field                  | Type                  | Required | Constraints                         | Description                                                                 |
|------------------------|-----------------------|----------|-------------------------------------|-----------------------------------------------------------------------------|
| `patientId`            | `UUID`                | Yes      | Non-null                            | UUID of the admitted patient (`patients.id`).                               |
| `shiftId`              | `UUID`                | Yes      | Non-null                            | UUID of the active medical shift.                                           |
| `physicalVentilatorId` | `UUID`                | Yes      | Non-null                            | UUID of the physical ventilator used for this reading.                      |
| `brand`                | `String` (enum)       | Yes      | `TECME` or `NEUMOVENT`              | Ventilator brand. Selects the strategy for brand-specific unit conversions. |
| `f`                    | `Number`              | Yes      | 0 ≤ f ≤ 80                          | Respiratory rate (breaths/min).                                            |
| `vt`                   | `Number`              | Yes      | > 0                                 | Tidal volume in **mL**.                                                     |
| `pao2`                 | `Number`              | Yes      | 0 ≤ pao2 ≤ 700                      | Arterial O₂ partial pressure (mmHg).                                       |
| `fio2`                 | `Number`              | Yes      | 0.21 ≤ fio2 ≤ 1.00                  | Fraction of inspired O₂ (dimensionless).                                   |
| `pplat`                | `Number`              | Yes      | > 0 and **> `peep`**               | Plateau airway pressure (cmH₂O).                                           |
| `peep`                 | `Number`              | Yes      | ≥ 0                                 | Total PEEP (cmH₂O).                                                        |
| `extendedParameters`   | `Object` (JSON map)   | No       | Free-form                           | Optional brand-specific extras stored as JSONB (e.g. `triggerFlow`).       |

> **Cross-field rule:** `pplat` must be strictly greater than `peep`. A violation returns
> `400` with the error keyed under `pplatGreaterThanPeep`. The same rule is enforced by a
> database CHECK constraint and by the math engine as defence-in-depth.

### Request Example

```json
{
  "patientId":            "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "shiftId":              "bbbbbbbb-0000-0000-0000-000000000001",
  "physicalVentilatorId": "cccccccc-0000-0000-0000-000000000001",
  "brand": "TECME",
  "f":    15,
  "vt":   500,
  "pao2": 85,
  "fio2": 0.40,
  "pplat": 25,
  "peep":   5
}
```

### Response — 201 Created

```json
{
  "status": 201,
  "message": "Evaluación registrada exitosamente",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "patientId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
    "physicalVentilatorId": "cccccccc-0000-0000-0000-000000000001",
    "evaluationTime": "2026-06-06T10:00:00-03:00",
    "f": 15,
    "vt": 500,
    "pao2": 85,
    "fio2": 0.40,
    "pplat": 25,
    "peep": 5,
    "rsbiSnapshot": 30.00,
    "rsbiInterpretation": "FAVORABLE",
    "pafiSnapshot": 212.50,
    "pafiClassification": "MILD_ARDS",
    "cstatSnapshot": 25.00,
    "cstatInterpretation": "LOW",
    "alertTriggered": false,
    "createdBy": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### Response Schema — `EvaluationResponse`

| Field                  | Type                          | Description                                                       |
|------------------------|-------------------------------|-------------------------------------------------------------------|
| `id`                   | `UUID`                        | Unique evaluation UUID.                                           |
| `patientId`            | `UUID`                        | Patient UUID.                                                     |
| `shiftId`              | `UUID`                        | Medical shift UUID.                                              |
| `physicalVentilatorId` | `UUID`                        | Physical ventilator UUID.                                        |
| `evaluationTime`       | `String` (ISO-8601 date-time) | Timezone-aware timestamp set on insert.                          |
| `f`                    | `Number`                      | Respiratory rate (breaths/min).                                  |
| `vt`                   | `Number`                      | Tidal volume (mL).                                               |
| `pao2`                 | `Number`                      | Arterial O₂ partial pressure (mmHg).                            |
| `fio2`                 | `Number`                      | Fraction of inspired O₂.                                        |
| `pplat`                | `Number`                      | Plateau airway pressure (cmH₂O).                               |
| `peep`                 | `Number`                      | Total PEEP (cmH₂O).                                            |
| `rsbiSnapshot`         | `Number`                      | Computed RSBI (2 dp).                                            |
| `rsbiInterpretation`   | `String` (enum)               | `FAVORABLE`, `BORDERLINE`, `UNFAVORABLE`.                        |
| `pafiSnapshot`         | `Number`                      | Computed PaFi (2 dp).                                            |
| `pafiClassification`   | `String` (enum)               | `NORMAL`, `AT_RISK`, `MILD_ARDS`, `MODERATE_ARDS`, `SEVERE_ARDS`.|
| `cstatSnapshot`        | `Number`                      | Computed Cstat (2 dp).                                           |
| `cstatInterpretation`  | `String` (enum)               | `HIGH`, `NORMAL`, `LOW`.                                         |
| `alertTriggered`       | `Boolean`                     | `true` if any clinical threshold was breached.                  |
| `createdBy`            | `UUID`                        | UUID of the therapist who submitted the evaluation.            |

### Interpretation thresholds

**RSBI** (Yang & Tobin):

| Value           | Interpretation |
|-----------------|----------------|
| `< 80`          | `FAVORABLE`    |
| `80 – 105`      | `BORDERLINE`   |
| `> 105`         | `UNFAVORABLE`  |

**PaFi** (Berlin Definition):

| Value (mmHg)    | Classification  |
|-----------------|-----------------|
| `≥ 400`         | `NORMAL`        |
| `300 – 399`     | `AT_RISK`       |
| `200 – 299`     | `MILD_ARDS`     |
| `100 – 199`     | `MODERATE_ARDS` |
| `< 100`         | `SEVERE_ARDS`   |

**Cstat** (mL/cmH₂O):

| Value           | Interpretation |
|-----------------|----------------|
| `≥ 100`         | `HIGH`         |
| `50 – 99`       | `NORMAL`       |
| `< 50`          | `LOW`          |

### Error Responses

| HTTP Status        | Condition                                                                                       |
|--------------------|-------------------------------------------------------------------------------------------------|
| `400 Bad Request`  | Validation failure — required field missing, value out of range, unknown `brand`, or `pplat ≤ peep`. |
| `401 Unauthorized` | Missing or invalid Bearer token with `ROLE_THERAPIST` (staging/prod only).                      |

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
