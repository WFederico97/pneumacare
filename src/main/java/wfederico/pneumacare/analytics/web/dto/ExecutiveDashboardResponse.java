package wfederico.pneumacare.analytics.web.dto;

/**
 * Flat executive dashboard payload for the Hospital Director.
 *
 * @param occupancyRatePercent        ICU bed occupancy as a percentage (0.0 when no beds)
 * @param alertFrequencyLast7Days     clinical alerts logged in the last 7 days
 * @param equipmentInMaintenanceCount physical ventilators currently in maintenance
 */
public record ExecutiveDashboardResponse(
        double occupancyRatePercent,
        long alertFrequencyLast7Days,
        long equipmentInMaintenanceCount) {
}
