# PneumaCare — End-to-End Findings

**Date:** 2026-07-24
**Method:** Playwright against the running production stack (`pneumacare-prod`, `prod` profile, Flyway V30, demo dataset), backed by direct DB and API inspection to confirm each cause.
**Scope covered:** auth and route guards, self-registration, patient admission, clinical history, airway events, SBT, discharge, shift open/close/handover/history, bed creation, ventilator inventory and assignment, alerts, analytics, executive dashboard, users, settings, account, legal pages, unknown routes.

Severity is about clinical and data risk, not effort.

| # | Severity | Finding | Status |
|---|---|---|---|
| 1 | **Critical** | Anonymous self-registration grants clinical roles and full patient PII | **Fixed** (5c25c4d) |
| 2 | **High** | Patient admission is broken — hardcoded ICU id | **Fixed** (5c25c4d) |
| 3 | **High** | JWT stays valid after the user is deleted or disabled | **Fixed** (5c25c4d) |
| 4 | **Medium** | New ventilators are filed in the wrong ICU | **Fixed** (5c25c4d) |
| 5 | **Medium** | Ventilators can be assigned across ICUs | **Fixed** (5c25c4d) |
| 6 | **Medium** | Ventilator inventory is not ICU-scoped | **Fixed** (5c25c4d) |
| 7 | **Low** | Closed episodes still show clinical action buttons | **Fixed** (8fca00b) |
| 8 | **Low** | No password change for the signed-in user | **Fixed** (e40c0c1 / 8fca00b) |
| 9 | **Low** | Stale success message persists next to a new error | **Fixed** (8fca00b) |

Verified-correct behaviour is listed at the end — several things that looked like defects were deliberate and documented.

---

## 1. Anonymous self-registration grants clinical roles and full patient PII — Critical

**What happens.** `/register` is reachable without authentication and offers "Terapista" and "Jefe de guardia". Submitting it creates the account, issues a session cookie immediately, and lands the visitor inside the app.

**Reproduced.** Logged out entirely, opened `/register`, created `e2e_intruder` as *Jefe de guardia*, and was authenticated without any approval step. `/patients` then returned every patient's full name and DNI:

```
Sofia Ferrari     DNI 51111100   Internado
Elena Morales     DNI 35555555   Internado
Roberto Ibáñez    DNI 34444444   Internado
...
```

The same account could open any clinical history and had working controls for **Egresar paciente**, **Asignar equipo**, **Agregar evento** and **Cerrar turno**.

**Why it matters.** The codebase encrypts PII at rest specifically for Law 25.326 (`AES_SECRET_KEY` is mandatory, `patient_identities` is separated from `patients`). That control is nullified if the application hands the decrypted data to anyone who fills in a form. This is the single most serious issue found.

**Cause.** `AuthController.register` is annotated `@PreAuthorize("permitAll()")` and `SecurityConfig` permits `POST /api/v1/auth/register`. The endpoint then issues a JWT for the new user:

```java
@PreAuthorize("permitAll()")
@PostMapping("/register")
public ResponseEntity<ApiResponseBase<LoginResponse>> register(@Valid @RequestBody RegisterRequest request) {
    if (!SELF_REGISTERABLE_ROLES.contains(request.role())) { ... }
    ...
    // Issue the session immediately so the SPA lands authenticated after sign-up.
    return authenticatedResponse(UserPrincipal.from(saved), "Registro exitoso");
}
```

**Fix walkthrough.** Staff accounts should be provisioned, not self-served. The admin user-management screen already exists and already enforces the admin boundary correctly, so the capability is not lost.

1. Delete the endpoint and its route:
   - Remove the `register` method from `AuthController` and `SELF_REGISTERABLE_ROLES`.
   - In `SecurityConfig`, drop `/api/v1/auth/register` from both the `permitAll` matcher (line ~113) and the CSRF `ignoringRequestMatchers` list (line ~107).
2. Remove the client route: delete `src/app/features/register/`, its entry in `app.routes.ts`, and the "Registrate" link on the login page.
3. Add a regression test asserting the endpoint is gone, so it cannot return quietly:
   ```java
   @Test
   void registerEndpointIsNotExposed() throws Exception {
       mockMvc.perform(post("/api/v1/auth/register")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"username\":\"x\",\"password\":\"y\",\"displayName\":\"z\",\"role\":\"ROLE_THERAPIST\"}"))
               .andExpect(status().isNotFound());
   }
   ```
4. Audit existing rows before deploying — any account created through this path is unvetted:
   ```sql
   SELECT id, username, display_name FROM users WHERE username <> 'admin';
   ```

**If self-registration must stay** (e.g. for the academic demo), then it must not grant access on its own: create the user `enabled = false` with no roles, return 202 instead of a session, and require an admin to enable and assign the role. That keeps the sign-up screen while removing the breach.

*(The `e2e_intruder` account created for this test was deleted; only `admin` remains.)*

---

## 2. Patient admission is broken — hardcoded ICU id — High

**What happens.** Admitting a patient always fails with:

> No se pudo registrar el paciente. Verifica los datos e intenta nuevamente.

**Reproduced.** Created bed `E2E-01` (succeeded), clicked it on the bed grid, filled the admission form correctly, submitted. Request body:

```json
{"firstName":"Paciente","lastName":"E2E","birthDate":"1975-03-14",
 "identifier":{"identifierTypeId":1,"value":"99887766"},
 "icuId":"cccccccc-0000-0000-0000-000000000001",
 "bedId":"0b39b5ea-f6b1-44a4-b392-c46b7e1d62fd"}
```

Response `400`: *"No se encontró la cama en la UCI indicada. Verifique bedId e icuId"*.

Every bed in the deployment belongs to `eeeeeeee-…` (DEMO-ICU), but the client sends `cccccccc-…` (UTI-01):

```
 bed_number |                icu_id                |   code
 DEMO-01    | eeeeeeee-0000-0000-0000-000000000001 | DEMO-ICU
 E2E-01     | eeeeeeee-0000-0000-0000-000000000001 | DEMO-ICU
```

Re-issuing the identical request with the session's real ICU returned `201`, proving the backend is correct and the client constant is the whole defect.

**Why it matters.** No patient can be admitted through the UI in this deployment. It is invisible in dev only because the dev seeder happens to use `cccccccc-…`. Bed creation already derives the ICU server-side ("Registra una cama para la UCI asociada a tu sesión"), so the two halves of the same screen disagree.

**Cause.** `src/app/dashboard/admission-modal/admission-modal.ts`:

```ts
const DEV_DEFAULT_ICU_ID = 'cccccccc-0000-0000-0000-000000000001';
...
/** ... until that lands this falls back to the seeded dev ICU. */
private resolveIcuId(): string | null {
  return DEV_DEFAULT_ICU_ID;
}
```

**Fix walkthrough.** Do not send the ICU at all — derive it server-side, exactly as shifts and evaluations already do via `CurrentIcuPort`.

1. Backend — `PatientIdentityService.create`: stop reading `request.icuId()` and resolve `currentIcuPort.currentIcuId()` instead; validate that the bed belongs to that ICU. Inject `CurrentIcuPort` (already used by `MedicalShiftService`).
2. Remove `icuId` from `CreatePatientRequest`, and document why in the record javadoc, matching the note added to `CreateEvaluationRequest`.
3. Frontend — delete `DEV_DEFAULT_ICU_ID` and `resolveIcuId()` from the admission modal and drop `icuId` from the payload.
4. Test: admit into a bed of the session ICU and assert `201`; then assert that a bed from another ICU yields `400`, which is the ICU-scoping check that survives the change.

---

## 3. JWT stays valid after the user is deleted or disabled — High

**What happens.** Deleting a user does not end their session. Their cookie keeps working until the token expires (`app.security.jwt.expiration: PT8H`).

**Reproduced.** Deleted `e2e_intruder` from the database, then — still holding that browser session — called `/api/v1/patients` and received `200` with the full patient list.

**Why it matters.** Offboarding and incident response are ineffective: a dismissed or compromised clinician keeps full clinical access for up to eight hours, and "Deshabilitar" in the user screen gives false assurance.

**Cause.** Authentication is a stateless self-issued JWT validated by the OAuth2 resource server (`CookieBearerTokenResolver` + `JwtService`). Validation checks signature and expiry only; no user row is consulted, so `enabled` and deletion are never seen after issuance.

**Fix walkthrough.** Two viable levels — the first is small and closes the practical gap:

1. **Per-request user check (recommended).** Add a lightweight filter after JWT authentication that loads the user by the `sub` claim and rejects when missing or `enabled = false`:
   ```java
   // reject when the account behind a still-valid token is gone or disabled
   UUID userId = UUID.fromString(jwt.getSubject());
   userRepository.findById(userId)
           .filter(UserJpaEntity::isEnabled)
           .orElseThrow(() -> new InsufficientAuthenticationException("Cuenta deshabilitada"));
   ```
   One indexed primary-key lookup per request; cache briefly if it shows up in profiling.
2. **Token versioning** for finer control: add `users.token_version`, embed it as a claim, bump it on disable/role change/password reset, and reject mismatches. This also invalidates tokens when a role is downgraded — which the check above does not.

Also shorten `PT8H`; an ICU shift is long but an eight-hour bearer token with no revocation is the outer bound of the damage.

---

## 4. New ventilators are filed in the wrong ICU — Medium

**Reproduced.** Created `E2E-VENT-001` from the Ventiladores screen while the session was in DEMO-ICU. It landed in UTI-01:

```
 serial_number |   icu
 DEMO-VENT-001 | DEMO-ICU
 DEMO-VENT-003 | UTI-01     ← previously created through the UI
 E2E-VENT-001  | UTI-01     ← created during this test
```

**Cause.** The same constant as finding 2, second copy: `src/app/features/ventilators/ventilators.ts` sends `icuId: DEV_DEFAULT_ICU_ID`.

**Fix walkthrough.** Same shape as finding 2 — derive server-side in `VentilatorService`/its controller from `CurrentIcuPort`, drop `icuId` from `CreateVentilatorRequest`, delete the constant from the component. Then repair existing rows:

```sql
UPDATE physical_ventilators
SET icu_id = 'eeeeeeee-0000-0000-0000-000000000001'
WHERE serial_number = 'DEMO-VENT-003';
```

`DEMO-VENT-003` is currently misfiled in the live demo database and should be corrected regardless of when the code fix lands.

---

## 5. Ventilators can be assigned across ICUs — Medium

**Reproduced.** Assigned `E2E-VENT-001` (UTI-01) to patient *Paciente E2E* (DEMO-ICU) via `POST /api/v1/assets/assign` → `200 "Ventilador asignado exitosamente"`. No ICU check anywhere in the path.

**Why it matters.** The executive asset-utilization matrix and the per-ICU inventory become wrong: a unit can show equipment in use that physically belongs to another unit. It also masks finding 4 — misfiled equipment still appears usable.

**Fix walkthrough.** In `AssetAssignmentService.assign`, resolve both sides and reject a mismatch before writing:

```java
UUID patientIcuId = patientLookupPort.findEpisode(request.patientId())
        .orElseThrow(() -> new BusinessLayerException(PATIENT_NOT_FOUND + request.patientId(), HttpStatus.NOT_FOUND))
        .icuId();
if (!ventilator.getIcuId().equals(patientIcuId)) {
    throw new BusinessLayerException(
            "El ventilador pertenece a otra UCI", HttpStatus.CONFLICT);
}
```

Note this needs the patient's ICU inside the `inventory` context — reuse `PatientLookupPort` (it already exposes `PatientEpisodeView.icuId`) rather than importing patient persistence directly. Add a unit test for the mismatch case, and one asserting a same-ICU assignment still succeeds.

While there: `assign` does not check that the episode is open either, so a discharged patient can be given a ventilator. Add the same `episodeOpen` guard used by airway/SBT/evaluations.

---

## 6. Ventilator inventory is not ICU-scoped — Medium

**Reproduced.** The Ventiladores list shows all four ventilators including those belonging to UTI-01, while the session is in DEMO-ICU.

**Why it matters.** Same class of issue as the shift-close scoping fixed earlier today: a unit sees and can act on another unit's resources. With more than one ICU in the database this is cross-tenant exposure of asset data.

**Fix walkthrough.** Scope the listing query by `currentIcuPort.currentIcuId()` in the ventilator service (`findByIcuId(...)` rather than `findAll()`), and apply the same scoping to delete/maintenance transitions so an out-of-ICU id returns `404`. Mirror the decision already taken for `MedicalShiftService.close`: report out-of-scope resources as `404`, not `403`, so the endpoint does not confirm their existence.

---

## 7. Closed episodes still show clinical action buttons — Low

**Reproduced.** Opened the history of *Carlos Giménez* (status **Alta**). "Egresar paciente" is correctly hidden, but **Asignar equipo** and **+ Agregar evento** remain enabled. Both now fail server-side with `409 "El episodio del paciente está cerrado"`.

**Why it matters.** Cosmetic only — the server guard added today holds — but it is a dead end that invites the user to fill a form that cannot succeed.

**Fix walkthrough.** In `patient-detail.ts`, add an episode-open signal and gate the controls with it:

```ts
readonly isEpisodeOpen = computed(() => this.patient()?.clinicalStatus === 'ADMITTED');
```

Use `@if (isEpisodeOpen())` around the "Asignar equipo" and "Agregar evento" controls (`canDischarge()` already includes this check). Show a short line such as *"Episodio cerrado: solo lectura."* so the absence of controls is explained rather than mysterious.

---

## 8. No password change for the signed-in user — Low

**Reproduced.** `/account` shows "Edición de datos — Próximamente"; there is no password-change control anywhere in the app.

**Why it matters.** `.env.prod.example` documents the bootstrap admin password as *"Rotate on first login"* — which is impossible today. A leaked or shared password can only be changed by an admin editing another user, and the sole admin cannot rotate their own.

**Fix walkthrough.** Add `POST /api/v1/auth/password` taking `currentPassword` + `newPassword`, verifying the current password with the existing `PasswordEncoder`, applying the same validation rules as registration, and re-issuing the session cookie. Pair it with finding 3's token versioning so changing a password invalidates other sessions. Then replace the "Próximamente" placeholder on `/account` with the form.

---

## 9. Stale success message persists next to a new error — Low

**Reproduced.** On `/beds/new`, created `E2E-01` (success message shown), then submitted `E2E-01` again. The page then displayed both at once:

```
Ya existe una cama con ese número.
Cama E2E-01 creada con estado AVAILABLE.
```

**Fix walkthrough.** In the bed-creation component, clear the success signal at the start of `submit()` (and clear the error on success). The other forms reviewed — admission, airway, discharge — already reset `submitError` on submit; this one only resets one direction.

---

## Verified correct

Worth recording, because several of these looked suspicious until checked:

- **Route guards.** Unauthenticated access to any protected route redirects to `/login?returnUrl=…`, and the return URL is honoured after login.
- **Admin boundary.** A chief of guard cannot modify an admin account (`403 "Solo un administrador puede modificar una cuenta de administrador"`) nor create one (`403 "Solo un administrador puede asignar el rol de administrador"`). Enforced at the API, not merely by disabling buttons.
- **HttpOnly session cookie.** `document.cookie` cannot read `PNMC_AT` from page scripts.
- **Login failure handling.** Wrong credentials produce a Spanish message and a `401`, with no user-enumeration difference between unknown user and wrong password.
- **Shift state machine.** `OPEN → CLOSED → OPEN` works; a second open in the same ICU is rejected (`409 "Ya existe un turno abierto para esta UCI"`); with no open shift, evaluations, airway events and handovers are all correctly rejected `409`.
- **Airway state machine.** Server-published transitions render correctly; from `TRACHEOSTOMY` only *Decanulación* is offered and it returns the patient to `SPONTANEOUS`.
- **Discharge.** One action closes the episode, frees the bed, releases the ventilator, and moves the executive metrics.
- **Empty alerts page.** Not a failure: the only admitted patient near a threshold has PaFi exactly 150, and `RiskThresholdEvaluator` documents boundary values as safe (`breach when value < 150`).
- **Analytics figures.** Internally consistent with the database, including the `—` placeholder for extubation success when the window genuinely contains no extubations.
- **Duplicate bed rejection**, unknown routes redirecting to the dashboard, and no unexplained console errors anywhere in the sweep — every error logged during testing came from a deliberate negative case.

---

## Suggested order

1. Finding 1 — it is a live PII breach and the fix is deletion, not design.
2. Finding 2 — the app cannot admit patients, so it is not demonstrable end-to-end.
3. Finding 3 — makes offboarding real.
4. Findings 4–6 together — one root cause (client-supplied ICU) and one consistent remedy (derive server-side, scope by session ICU).
5. Findings 7–9 — polish.

Findings 2, 4, 5 and 6 are all the same underlying mistake: **the client is trusted to say which ICU it is acting in.** The pattern already used for shifts and, as of today, evaluations — resolve scope from the session server-side and reject anything outside it — is the general fix.

## Test data

All E2E artefacts were removed: user `e2e_intruder`, patient *Paciente E2E*, bed `E2E-01`, ventilator `E2E-VENT-001` and its assignment. The demo dataset is intact (13 patient episodes, 6 beds, 3 ventilators, one open shift). `DEMO-VENT-003` remains misfiled in UTI-01 — pre-existing, see finding 4.


---

## Fix verification (2026-07-24, after `5c25c4d` / frontend `f1be0ec`)

Each fix re-tested against the rebuilt production stack:

| # | Check | Result |
|---|---|---|
| 1 | `POST /auth/register` as anonymous | `403`, no session cookie, no user row created (`users = admin` only) |
| 2 | `POST /patients` with **no** `icuId` | `201 Patient admitted successfully` — previously impossible |
| 3 | Live session, account then disabled | `200` → `401`; also `401` after deletion |
| 4 | `POST /ventilators` with **no** `icuId` | filed in the session ICU (`eeeeeeee-…`) |
| 5 | Assign a UTI-01 ventilator to a DEMO-ICU patient | `409 "El ventilador pertenece a otra UCI"` |
| 5b | Assign to a closed episode | `409 "El episodio del paciente está cerrado"` |
| 6 | `GET /ventilators` | only the session ICU's three units |

434 backend unit tests pass. `DEMO-VENT-003` was moved back to DEMO-ICU. Verification
artefacts removed; demo dataset intact (1 user, 13 episodes, 6 beds, 3 ventilators).

**Observed while fixing, not yet addressed:** unmapped API paths return `500`
rather than `404` — `GlobalExceptionHandler` catches `NoResourceFoundException`
in its generic `Exception` handler. Harmless to clients but noisy for monitoring,
and it is why the finding-1 regression test asserts "no session issued" instead of
a `404` status.

### Findings 7–9 (fixed 2026-07-24, `e40c0c1` / frontend `8fca00b`)

| # | Check | Result |
|---|---|---|
| 7 | Discharged patient's history | no `Egresar` / `Asignar equipo` / `Agregar evento`; shows "Episodio cerrado: la historia clínica es de solo lectura." |
| 7 | Admitted patient's history | all three controls still present |
| 8 | Wrong current password | `403 "La contraseña actual es incorrecta"` |
| 8 | New password under 8 chars | `400` with a per-field message |
| 8 | New password same as current | `400 "La nueva contraseña debe ser distinta de la actual"` |
| 8 | Full rotation | change → old password `401`, new password `200` → changed back |
| 9 | Create a bed, then submit the same number | only the duplicate error shows; the success banner is cleared |

437 backend unit tests pass. Demo dataset intact and the demo admin password
restored to its original value.

### Follow-ups (fixed 2026-07-24, `e9ef4b3` / frontend `c786bdf`)

The three caveats left by the fixes above are now closed too.

| Item | Check | Result |
|---|---|---|
| Token versioning | Two sessions; one changes the password | the changing device stays `200`, the other session drops to `401` |
| Anonymous status | Unauthenticated `GET` and `POST` to protected endpoints | `401` (was `403` on POST — CSRF rejected before auth ran) |
| Unmapped paths | Authenticated request to `/api/v1/does-not-exist` | `404 "Recurso no encontrado"` (was `500`) |

`V31` adds `users.token_version`, embedded as a JWT claim and re-checked on every
request. A password change bumps it, ending every session minted under the old
password; the caller's own session is re-issued at the new generation so they are
not logged out. Tokens issued before the column existed carry no claim and are
treated as stale, so every user signs in once after deploy.

439 backend unit tests pass. The demo admin password was rotated during
verification and restored.

All nine findings and all follow-ups are closed.
