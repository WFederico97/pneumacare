package wfederico.pneumacare.clinical.web.dto;

import java.util.UUID;

/**
 * Consultant insight payload for a single evaluation.
 *
 * @param evaluationId the evaluation the insight belongs to
 * @param insightText  composed, reference-grounded guidance (citations inline)
 * @param cached       true when served from the store, false when just composed
 */
public record InsightResponse(UUID evaluationId, String insightText, boolean cached) {
}
