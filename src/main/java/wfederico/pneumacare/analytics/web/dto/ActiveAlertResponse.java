package wfederico.pneumacare.analytics.web.dto;

import java.time.OffsetDateTime;

/**
 * One currently-active clinical alert: an admitted patient whose most recent
 * evaluation tripped a threshold. Identified by bed/ICU (no PII) plus the
 * triggering metric snapshots so the ward can triage at a glance.
 */
public record ActiveAlertResponse(
        String patientId,
        String bedNumber,
        String icuName,
        OffsetDateTime evaluationTime,
        Double rsbi,
        String rsbiInterpretation,
        Double pafi,
        String pafiClassification,
        Double cstat,
        String cstatInterpretation) {
}
