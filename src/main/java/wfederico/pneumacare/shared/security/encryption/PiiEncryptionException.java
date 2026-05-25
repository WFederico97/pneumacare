package wfederico.pneumacare.shared.security.encryption;

/**
 * Thrown when AES-256-GCM encryption or decryption of a PII field fails.
 *
 * <p>This is an unchecked exception so it propagates through the JPA/Hibernate
 * call stack without requiring checked-exception declarations on every repository method.
 * A failure here signals a system-level misconfiguration (wrong key, corrupted data),
 * not a user input error, and should surface as HTTP 500.
 */
public class PiiEncryptionException extends RuntimeException {

    public PiiEncryptionException(String message) {
        super(message);
    }

    public PiiEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
