package wfederico.pneumacare.shared.constants;

/**
 * Utility class that centralizes validation constants (regex patterns, size limits).
 * Provides named constants to avoid raw string literals in {@code @Pattern} annotations
 * and comply with PMD rules.
 */
public final class ValidationConstants {
    private ValidationConstants() {}

    // ── DNI (Documento Nacional de Identidad) ────────────────────────────────

    /**
     * Regex that accepts a valid Argentine DNI number:
     * 7 or 8 consecutive digits, no dots, no spaces.
     *
     * <p>Examples: {@code "1234567"} ✓, {@code "35123456"} ✓,
     * {@code "12.345.678"} ✗, {@code "ABC123"} ✗.
     */
    public static final String DNI_PATTERN = "^\\d{7,8}$";

    /** Minimum length of a valid Argentine DNI (7 digits). */
    public static final int DNI_MIN_LENGTH = 7;

    /** Maximum length of a valid Argentine DNI (8 digits). */
    public static final int DNI_MAX_LENGTH = 8;
}
