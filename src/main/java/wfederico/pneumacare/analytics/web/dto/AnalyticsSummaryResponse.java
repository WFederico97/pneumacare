package wfederico.pneumacare.analytics.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Role-scoped analytics payload. A null section means the caller's role does not receive it. */
public record AnalyticsSummaryResponse(
        ClinicalAnalytics clinical,
        WardAnalytics ward,
        IamAnalytics iam) {

    public record ClinicalAnalytics(
            OccupancyStats occupancy,
            WeaningStats weaning,
            List<TrendPoint> evaluationTrend) {
    }

    public record OccupancyStats(
            long total, long occupied, long available, long maintenance, double occupancyRate) {
    }

    public record WeaningStats(
            int windowDays,
            long sbtSuccess, long sbtFailure,
            long rsbiFavorable, long rsbiBorderline, long rsbiUnfavorable) {
    }

    public record TrendPoint(LocalDate day, long count) {
    }

    public record WardAnalytics(boolean activeShiftOpen, long shiftsInWindow, long admissionsInWindow) {
    }

    public record IamAnalytics(long totalUsers, long enabledUsers, long disabledUsers, Map<String, Long> byRole) {
    }
}
