package wfederico.pneumacare.analytics.web.dto;

/**
 * Flat executive dashboard payload for the Hospital Director.
 *
 * @param occupancyRatePercent        ICU bed occupancy as a percentage (0.0 when no beds)
 * @param alertFrequencyLast7Days     clinical alerts logged in the last 7 days
 * @param equipmentInMaintenanceCount physical ventilators currently in maintenance
 * @param assetUtilization            ventilator fleet status matrix + utilization
 * @param averageStayDays             mean current length of stay of admitted patients,
 *                                    in days (a proxy for ALOS until discharge dates
 *                                    are tracked)
 */
public record ExecutiveDashboardResponse(
        double occupancyRatePercent,
        long alertFrequencyLast7Days,
        long equipmentInMaintenanceCount,
        AssetUtilization assetUtilization,
        double averageStayDays) {

    /**
     * Ventilator fleet status matrix. {@code utilizationPercent} is
     * {@code inUse / (inUse + available + maintenance)}.
     */
    public record AssetUtilization(
            long inUse, long available, long maintenance, double utilizationPercent) {
    }
}
