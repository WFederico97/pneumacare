package wfederico.pneumacare.analytics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse.AssetUtilization;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorRepository;
import wfederico.pneumacare.notification.infrastructure.persistence.ClinicalAlertLogRepository;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Read-only executive read-model: aggregates ICU operational metrics for the
 * Hospital Director dashboard. Cross-context by design (beds, alerts, ventilators),
 * aggregate-only — never reads or returns individual patient records.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveAnalyticsService {

    private static final int ALERT_WINDOW_DAYS = 7;

    private final IcuBedRepository beds;
    private final ClinicalAlertLogRepository alerts;
    private final PhysicalVentilatorRepository ventilators;
    private final PatientRepository patients;

    @Transactional(readOnly = true)
    public ExecutiveDashboardResponse dashboard() {
        long occupied = beds.countByStatus(BedStatus.OCCUPIED);
        long totalBeds = beds.count();
        double occupancyRatePercent = totalBeds == 0
                ? 0.0
                : round2(occupied * 100.0 / totalBeds);

        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(ALERT_WINDOW_DAYS);
        long alertFrequency = alerts.countByCreatedAtAfter(since);

        AssetUtilization assetUtilization = buildAssetUtilization();

        return new ExecutiveDashboardResponse(
                occupancyRatePercent,
                alertFrequency,
                assetUtilization.maintenance(),
                assetUtilization,
                averageStayDays());
    }

    private AssetUtilization buildAssetUtilization() {
        long inUse = ventilators.countByStatus(VentilatorStatus.IN_USE);
        long available = ventilators.countByStatus(VentilatorStatus.AVAILABLE);
        long maintenance = ventilators.countByStatus(VentilatorStatus.MAINTENANCE);
        long total = inUse + available + maintenance;
        double utilizationPercent = total == 0 ? 0.0 : round2(inUse * 100.0 / total);
        return new AssetUtilization(inUse, available, maintenance, utilizationPercent);
    }

    /** Mean current stay of admitted patients in days; 0 when none are admitted. */
    private double averageStayDays() {
        List<OffsetDateTime> admissions =
                patients.findAdmissionDatesByClinicalStatus(ClinicalStatus.ADMITTED);
        if (admissions.isEmpty()) {
            return 0.0;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        double totalDays = admissions.stream()
                .mapToDouble(a -> Duration.between(a, now).toMinutes() / (60.0 * 24.0))
                .sum();
        return Math.round(totalDays / admissions.size() * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
