package wfederico.pneumacare.analytics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorRepository;
import wfederico.pneumacare.notification.infrastructure.persistence.ClinicalAlertLogRepository;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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

    @Transactional(readOnly = true)
    public ExecutiveDashboardResponse dashboard() {
        long occupied = beds.countByStatus(BedStatus.OCCUPIED);
        long totalBeds = beds.count();
        double occupancyRatePercent = totalBeds == 0
                ? 0.0
                : BigDecimal.valueOf(occupied * 100.0 / totalBeds)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();

        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(ALERT_WINDOW_DAYS);
        long alertFrequency = alerts.countByCreatedAtAfter(since);

        long maintenance = ventilators.countByStatus(VentilatorStatus.MAINTENANCE);

        return new ExecutiveDashboardResponse(occupancyRatePercent, alertFrequency, maintenance);
    }
}
