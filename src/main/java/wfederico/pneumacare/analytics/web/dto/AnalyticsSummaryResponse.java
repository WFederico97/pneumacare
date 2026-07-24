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
            List<TrendPoint> evaluationTrend,
            LungProtectionStats lungProtection,
            VentilationStats ventilation,
            WeaningClassificationStats weaningClassification,
            ExtubationStats extubation) {
    }

    /**
     * Extubation outcomes within the window.
     *
     * @param extubations        extubation events recorded in the window
     * @param reintubations48h   of those, the ones followed by a re-intubation of the
     *                           same patient within 48 h (an extubation failure)
     * @param successRatePercent (extubations − reintubations) / extubations × 100
     */
    public record ExtubationStats(long extubations, long reintubations48h, double successRatePercent) {
    }

    /**
     * WIND-aligned weaning difficulty across currently-ventilated patients,
     * approximated from SBT attempt counts (Béduneau et al. 2017):
     * {@code noAttempt} (no SBT yet), {@code simple} (1 attempt), {@code difficult}
     * (2–3 attempts) and {@code prolonged} (&gt; 3 attempts).
     */
    public record WeaningClassificationStats(
            long noAttempt, long simple, long difficult, long prolonged) {
    }

    /**
     * Lung-protective-ventilation surveillance. {@code highDrivingPressurePatients}
     * is the count of patients whose latest evaluation carries ΔP &gt; 15 cmH₂O —
     * a mortality-associated threshold (Amato 2015).
     */
    public record LungProtectionStats(long highDrivingPressurePatients) {
    }

    /**
     * Invasive mechanical ventilation load.
     *
     * @param currentlyIntubated       patients presently in the INTUBATED airway state
     * @param intubationDaysInWindow   endotracheal-intubation patient-days that fall
     *                                 within the reporting window (intervals folded
     *                                 from the airway-event log; a still-open
     *                                 intubation is counted up to now)
     */
    public record VentilationStats(long currentlyIntubated, double intubationDaysInWindow) {
    }

    public record OccupancyStats(
            long total, long occupied, long available, double occupancyRate) {
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
