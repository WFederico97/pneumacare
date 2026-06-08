package wfederico.pneumacare.shared.constants;

/**
 * Utility class that centralizes error messages and business messages.
 * Provides constants for error messages to avoid duplicate string literals
 * and comply with PMD rules.
 */
public final class ExceptionMessageConstants {
    private ExceptionMessageConstants() {}

    // ── Clinical math ────────────────────────────────────────────────────────

    /** Error message when pplat is {@code <=} peepTotal in calculateCstat. */
    public static final String CSTAT_FORMULA_ERROR = "La presión meseta debe ser mayor que el PEEP total";

    // ── Strategy ─────────────────────────────────────────────────────────────

    /** Error message when the ventilator brand is not found. */
    public static final String UNKNOWN_BRAND_ERROR = "Marca de ventilador desconocida: ";

    // ── Patient admission ────────────────────────────────────────────────────

    /** The requested ICU does not exist. */
    public static final String ICU_NOT_FOUND = "No se encontró la UCI con id: ";

    /** The requested bed does not exist or does not belong to the given ICU. */
    public static final String BED_NOT_FOUND =
            "No se encontró la cama en la UCI indicada. Verifique bedId e icuId";

    /** The requested bed exists but its status is not AVAILABLE. */
    public static final String BED_NOT_AVAILABLE =
            "La cama solicitada no está disponible. Estado actual: ";

    /** A patient record already exists for the given identity. */
    public static final String PATIENT_ALREADY_ADMITTED =
            "El paciente ya se encuentra internado con id: ";
}
