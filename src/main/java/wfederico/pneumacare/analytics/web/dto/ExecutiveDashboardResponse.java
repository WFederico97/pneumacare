package wfederico.pneumacare.analytics.web.dto;

/**
 * Flat executive dashboard payload for the Hospital Director.
 *
 * @param occupancyRatePercent        ICU bed occupancy as a percentage (0.0 when no beds)
 * @param alertFrequencyLast7Days     clinical alerts logged in the last 7 days
 * @param equipmentInMaintenanceCount physical ventilators currently in maintenance
 * @param assetUtilization            ventilator fleet status matrix + utilization
 * @param averageStayDays             true ALOS: mean stay (in days) of episodes
 *                                    closed in the 30-day window; 0.0 when none
 * @param currentCensusMeanStayDays   mean current stay of presently admitted
 *                                    patients (the pre-V29 proxy, kept for the
 *                                    census view)
 * @param bedTurnover                 episodes closed in the window / total beds
 * @param mortality                   episode mortality aggregation (window)
 * @param readmissions                readmission counts and rates (window)
 */
public record ExecutiveDashboardResponse(
        double occupancyRatePercent,
        long alertFrequencyLast7Days,
        long equipmentInMaintenanceCount,
        AssetUtilization assetUtilization,
        double averageStayDays,
        double currentCensusMeanStayDays,
        double bedTurnover,
        MortalityStats mortality,
        ReadmissionStats readmissions) {

    /**
     * Ventilator fleet status matrix. {@code utilizationPercent} is
     * {@code inUse / (inUse + available + maintenance)}.
     */
    public record AssetUtilization(
            long inUse, long available, long maintenance, double utilizationPercent) {
    }

    /**
     * Episode mortality over the 30-day window. {@code withdrawalOfCare} is
     * reported separately from {@code deceased}; {@code icuMortalityPercent}
     * counts both against all closed episodes. The weaning-failure cohort is
     * closed episodes with ≥1 failed SBT or a 48 h reintubation.
     */
    public record MortalityStats(
            long closedEpisodes,
            long deceased,
            long withdrawalOfCare,
            double icuMortalityPercent,
            long weaningFailureCohort,
            long weaningFailureDeceased,
            double weaningFailureMortalityPercent) {
    }

    /** Readmissions of the same identity after a windowed discharge. */
    public record ReadmissionStats(
            long readmissions48h,
            long readmissions7d,
            double rate48hPercent,
            double rate7dPercent) {
    }
}
