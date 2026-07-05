package wfederico.pneumacare.clinical.domain;

import java.util.List;

/**
 * Output of {@link wfederico.pneumacare.clinical.application.ClinicalConsultantService}.
 *
 * <p>{@code text} is the composed, reference-grounded guidance string (the value
 * PNMC-106 will persist as {@code insight_text}). {@code sources} lists the
 * distinct {@code source_ref}s behind the guidance, also appended inline to
 * {@code text} as a trailing citation.
 *
 * <p>Derived solely from {@code medical_reference} rows — never carries PII.
 */
public record ConsultantGuidance(String text, List<String> sources) {}
