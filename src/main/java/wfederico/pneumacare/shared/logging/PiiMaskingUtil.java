package wfederico.pneumacare.shared.logging;

/**
 * Utility for producing PII-safe representations of sensitive values in log statements.
 *
 * <p>Use these methods whenever a log statement touches a value that is or may be
 * PII-adjacent. The underlying data is <em>never</em> modified — only the string
 * representation written to the log is masked.
 *
 * <h2>Usage</h2>
 * <pre>
 *   import static wfederico.pneumacare.shared.logging.PiiMaskingUtil.*;
 *
 *   log.info("Identity saved: firstName={}, dni={}", maskName(firstName), maskDni(dni));
 * </pre>
 *
 * <p>All methods handle {@code null} and blank strings gracefully.
 */
public final class PiiMaskingUtil {

    private PiiMaskingUtil() {}

    /**
     * Masks a DNI value, keeping only the first two and last two digits visible.
     *
     * <pre>
     *   "35123456" → "35****56"   (8-digit DNI)
     *   "3512345"  → "35***45"    (7-digit DNI)
     *   "12"       → "**"         (too short to partially reveal)
     *   null       → "[NULL]"
     * </pre>
     *
     * @param dni the plain-text DNI string
     * @return a masked representation safe for log output
     */
    public static String maskDni(String dni) {
        if (dni == null) {
            return "[NULL]";
        }
        int len = dni.length();
        if (len <= 4) {
            return "*".repeat(len);
        }
        return dni.substring(0, 2) + "*".repeat(len - 4) + dni.substring(len - 2);
    }

    /**
     * Masks a person's name, keeping only the first character visible.
     *
     * <pre>
     *   "Juan"  → "J***"
     *   "A"     → "A"
     *   ""      → "[BLANK]"
     *   null    → "[NULL]"
     * </pre>
     *
     * @param name the plain-text name (first or last)
     * @return a masked representation safe for log output
     */
    public static String maskName(String name) {
        if (name == null) {
            return "[NULL]";
        }
        if (name.isBlank()) {
            return "[BLANK]";
        }
        if (name.length() == 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /**
     * Fully redacts any sensitive value, returning the literal token {@code [REDACTED]}.
     *
     * <p>Use this for identifier values (CUIL, health insurance numbers, etc.) where
     * even a partial reveal is undesirable.
     *
     * @param value any PII string (may be null)
     * @return {@code "[REDACTED]"} for non-null input, {@code "[NULL]"} for null
     */
    public static String redact(String value) {
        return value == null ? "[NULL]" : "[REDACTED]";
    }
}
