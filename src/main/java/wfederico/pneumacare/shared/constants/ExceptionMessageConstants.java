package wfederico.pneumacare.shared.constants;

/**
 * Utility class that centralizes error messages and business messages.
 * Provides constants for error messages to avoid duplicate string literals
 * and comply with PMD rules.
 */
public final class ExceptionMessageConstants {
    private ExceptionMessageConstants() {}

    /* Error message when pplat is <= peepTotal in calculateCstat */
    public static final String CSTAT_FORMULA_ERROR = "La presión meseta debe ser mayor que el PEEP total";
}
