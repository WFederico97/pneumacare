# AES-256-GCM PII Encryption — PneumaCare

## Overview

PneumaCare stores patient Personally Identifiable Information (PII) encrypted at rest
in compliance with **Argentine Law 25.326 (Ley de Protección de Datos Personales / Habeas Data)**.

The encrypted columns are `patient_identities.first_name`, `patient_identities.last_name`,
and `patient_identities.national_id`. Encryption and decryption are **transparent** to all
application layers above JPA — services, controllers, and API consumers always work with
plain text.

---

## Algorithm

| Property            | Value                                              |
|---------------------|----------------------------------------------------|
| Algorithm           | AES-256-GCM (Galois/Counter Mode)                  |
| Key size            | 256 bits (32 bytes)                                |
| IV size             | 96 bits (12 bytes), randomly generated per write   |
| Authentication tag  | 128 bits                                           |
| Storage encoding    | Base64(`IV[12]` &#124;&#124; `Ciphertext+Tag`)     |
| Column type         | `TEXT` (see V2 migration)                          |

AES-GCM provides **authenticated encryption** — it detects ciphertext tampering in addition
to providing confidentiality. The random IV per write ensures **non-deterministic output**
(IND-CPA security): the same plaintext produces different ciphertext on every INSERT.

---

## Key Management

### Generating a key

```bash
openssl rand -base64 32
```

This produces a Base64-encoded 32-byte (256-bit) key. Example output (never use this):

```
cHV0QSByZWFsIGtleSBoZXJlISBEb24ndCB1c2UgdGhpcw==
```

### Configuring the key

Set the `AES_SECRET_KEY` environment variable. The application reads it through the
`app.security.encryption.aes-secret-key` property:

```yaml
# application.yml (auto-configured — do not hardcode the value)
app:
  security:
    encryption:
      aes-secret-key: ${AES_SECRET_KEY:}
```

**Local development:**

```bash
# Copy the template and fill in your key
cp .env.example .env
# Edit .env — replace the AES_SECRET_KEY placeholder with the output of:
# openssl rand -base64 32
```

**Docker Compose (full stack):**

```bash
# Set AES_SECRET_KEY in .env before running docker compose up
docker compose up
```

**CI / Production:**

Set `AES_SECRET_KEY` as a CI secret or via the container orchestration secrets store
(Kubernetes Secret, AWS Secrets Manager, etc.). Never commit the key to source control.

### Security rules

| Rule | Detail |
|------|--------|
| Never hardcode the key | The key must come from the environment, not source |
| Never commit the key | `.env` is in `.gitignore`; CI secrets are never logged |
| Rotate keys deliberately | Rotation requires re-encrypting all existing rows (see below) |
| Use unique keys per environment | Dev, staging, and prod must each have a different key |

---

## Fail-Fast Startup Validation

The application **refuses to start** if the key is missing or invalid. This prevents
patient PII from ever being stored unencrypted due to a misconfiguration.

`AesEncryptionConfig.aesSecretKeySpec()` runs during Spring context initialization and
throws `IllegalStateException` for any of the following conditions:

| Condition | Error message contains |
|-----------|------------------------|
| Key is null or blank | `AES_SECRET_KEY is not configured` |
| Key is not valid Base64 | `not valid Base64` |
| Decoded key ≠ 32 bytes | `32 bytes (256 bits)` + actual byte count |

**Example startup error:**

```
APPLICATION FAILED TO START
...
java.lang.IllegalStateException: Security configuration error: AES_SECRET_KEY is not configured.
Set the 'app.security.encryption.aes-secret-key' property via the AES_SECRET_KEY
environment variable. Application startup aborted to protect patient PII (Law 25.326).
Generate a valid key with: openssl rand -base64 32
```

---

## JPA AttributeConverter

`AesAttributeConverter` implements `jakarta.persistence.AttributeConverter<String, String>`.
It is a Spring `@Component` and a JPA `@Converter`, injected with the validated
`SecretKeySpec` bean from `AesEncryptionConfig`.

```
Plain text  ──encrypt──▶  Base64(IV || Ciphertext+Tag)  ──stored in DB──▶
            ◀──decrypt──  Base64(IV || Ciphertext+Tag)  ◀──read from DB──
```

Apply it to an entity field with:

```java
@Convert(converter = AesAttributeConverter.class)
@Column(name = "first_name", nullable = false, columnDefinition = "TEXT")
private String firstName;
```

> `autoApply = false` is the default — the converter is only active on fields
> explicitly annotated with `@Convert`. Other `String` fields are not affected.

---

## Database Schema

The encrypted columns are defined in `patient_identities`. The V2 migration altered their
type from `VARCHAR(255)` to `TEXT` to accommodate ciphertext of any realistic input length,
and dropped the `UNIQUE` constraint on `national_id`.

### Column sizing

For an input of `N` bytes, the stored Base64 length is approximately:

```
ceil((12 + N + 16) / 3) × 4  bytes of Base64
```

Examples:

| Input length | Stored Base64 chars |
|---|---|
| 10 chars (typical name) | ~52 |
| 50 chars | ~92 |
| 100 chars | ~172 |
| 255 chars | ~384 |

### UNIQUE constraint on `national_id`

Because the IV is random, two inserts of the same `national_id` produce different
ciphertext values. A DB-level `UNIQUE` constraint cannot detect duplicate plaintexts
and was therefore dropped in `V2__encrypt_patient_identity_columns.sql`.

Application-layer dedup via equality query is equally impossible — the query parameter
would be re-encrypted to a new ciphertext, never matching stored rows.
A future migration will introduce a `national_id_hash` column (HMAC-SHA256) with a
`UNIQUE` index for reliable deduplication. Duplicate national IDs are not prevented
by the current implementation.

---

## Performance

AES-256-GCM is hardware-accelerated on all modern CPUs (AES-NI instruction set).
Per-field encryption and decryption complete in < 1 ms — well within the 50 ms
per-transaction budget defined in the issue.

The `SecureRandom` instance used for IV generation is shared (thread-safe) and
seeded once at class load time — there is no per-request entropy-wait.

---

## Key Rotation

When the AES key must be rotated:

1. Generate a new key: `openssl rand -base64 32`
2. Write a background job that:
   - Reads each `patient_identities` row via the JPA repository (decrypts with old key)
   - Re-encrypts with the new key and writes the row back
3. Update `AES_SECRET_KEY` in all environments after the backfill completes
4. The JPA layer handles all crypto transparently — the job only needs a standard
   `findAll()` + `save()` cycle

> Never change the key without a full backfill — rows encrypted with the old key
> will fail to decrypt with the new one.

---

## Relevant Files

| File | Purpose |
|------|---------|
| `shared/security/encryption/AesEncryptionProperties.java` | `@ConfigurationProperties` binding |
| `shared/security/encryption/AesEncryptionConfig.java` | `SecretKeySpec` bean + fail-fast validation |
| `shared/security/encryption/AesAttributeConverter.java` | JPA converter (encrypt on write, decrypt on read) |
| `shared/security/encryption/PiiEncryptionException.java` | Unchecked exception for crypto failures |
| `patient/infrastructure/persistence/PatientIdentityJpaEntity.java` | Entity with `@Convert` on PII fields |
| `db/migration/V2__encrypt_patient_identity_columns.sql` | Widens columns to TEXT, drops UNIQUE on national_id |
| `.env.example` | Template with `AES_SECRET_KEY` placeholder |
| `test/.../AesEncryptionConfigTest.java` | Unit tests for startup validation (AC3) |
| `test/.../AesAttributeConverterTest.java` | Unit tests for encrypt/decrypt round-trip |
| `test/.../PatientPiiEncryptionIT.java` | Integration tests for AC1 and AC2 |
