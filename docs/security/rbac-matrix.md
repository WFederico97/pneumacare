# RBAC Matrix — PneumaCare

Source of truth for role → endpoint authorization. Defined in PNMC-135;
**enforced** by PNMC-114 (method security / request matchers, SCOPE→ROLE
migration). Roles come from the canonical `Role` enum
(`shared/security/user/Role.java`).

## Roles

| Role | Purpose |
|---|---|
| `ROLE_ADMIN` | Full access; user/role administration. |
| `ROLE_CHIEF_OF_GUARD` | Shift lifecycle (open/close), oversight reads, bed provisioning. |
| `ROLE_THERAPIST` | Clinical data entry — patients, evaluations, procedures, handovers. |
| `ROLE_COMPLIANCE` | Read + audit access (replaces the legacy `SCOPE_audit`). |

## Endpoint matrix

| Endpoint | Method | ADMIN | CHIEF_OF_GUARD | THERAPIST | COMPLIANCE |
|---|---|:-:|:-:|:-:|:-:|
| `/api/v1/health` | GET | public | public | public | public |
| `/api/v1/identifier-types` | GET | public | public | public | public |
| `/api/v1/patients` | POST | ✓ | ✓ | ✓ | |
| `/api/v1/patients/{id}` | GET | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/patients/{id}/timeline` | GET | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/icu-beds` | GET | ✓ | ✓ | ✓ | |
| `/api/v1/icu-beds` | POST | ✓ | ✓ | | |
| `/api/v1/evaluations` (+ `/rsbi`, `/pafi`, `/cstat`) | POST | ✓ | ✓ | ✓ | |
| `/api/v1/procedures/airway` | POST | ✓ | ✓ | ✓ | |
| `/api/v1/patients/{patientId}/airway-events` | GET | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/procedures/sbt` | POST | ✓ | ✓ | ✓ | |
| `/api/v1/procedures/sbt` | GET | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/shifts/active` | GET | ✓ | ✓ | ✓ | |
| `/api/v1/shifts` | POST | ✓ | ✓ | | |
| `/api/v1/shifts/{id}/close` | PATCH | ✓ | ✓ | | |
| `/api/v1/shifts/{shiftId}/handovers` | POST/GET | ✓ | ✓ | ✓ | |
| `/api/v1/shifts/{id}/audit` | GET | ✓ | | | ✓ |
| `/api/v1/shifts/handovers/{id}/audit` | GET | ✓ | | | ✓ |

Public endpoints require no authentication (see `SecurityConfig`). A blank cell
means the role is denied that endpoint.

**Role hierarchy (enforced by `RoleHierarchy`):**
`ROLE_ADMIN > ROLE_CHIEF_OF_GUARD > ROLE_THERAPIST` and `ROLE_ADMIN > ROLE_COMPLIANCE`.
A Chief of Guard therefore inherits all Therapist access (including clinical
entry); ADMIN inherits every role; COMPLIANCE inherits nothing.
