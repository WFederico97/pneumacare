package wfederico.pneumacare.analytics.web.dto;

import wfederico.pneumacare.analytics.application.HierarchyLevel;

import java.util.List;

/**
 * Multi-level analytics rollup: aggregated ICU metrics grouped by province,
 * institution, or patient. Access is restricted per level by the service (org
 * rollups are director/admin only).
 */
public record HierarchyAnalyticsResponse(
        HierarchyLevel level,
        int windowDays,
        List<HierarchyRow> rows) {

    /**
     * One aggregated entity. For org levels {@code name} is the province/hospital;
     * for the patient level {@code name} is the bed label and {@code subtitle} its ICU.
     */
    public record HierarchyRow(
            String entityId,
            String name,
            String subtitle,
            long totalBeds,
            long occupied,
            long available,
            int occupancyRatePercent,
            long activeAlerts,
            long evaluationsInWindow) {
    }
}
