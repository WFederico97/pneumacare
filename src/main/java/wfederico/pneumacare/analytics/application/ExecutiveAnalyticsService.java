package wfederico.pneumacare.analytics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse.AssetUtilization;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse.MortalityStats;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse.ReadmissionStats;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorRepository;
import wfederico.pneumacare.notification.infrastructure.persistence.ClinicalAlertLogRepository;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.domain.Disposition;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.procedures.domain.AirwayEventType;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventJpaEntity;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventRepository;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only executive read-model: aggregates ICU operational metrics for the
 * Hospital Director dashboard. Cross-context by design (beds, alerts, ventilators,
 * episodes), aggregate-only — never reads or returns individual patient records.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveAnalyticsService {

    private static final int ALERT_WINDOW_DAYS = 7;
    /** Lookback for episode metrics (ALOS, turnover, mortality, readmission). */
    private static final int EPISODE_WINDOW_DAYS = 30;

    private final IcuBedRepository beds;
    private final ClinicalAlertLogRepository alerts;
    private final PhysicalVentilatorRepository ventilators;
    private final PatientRepository patients;
    private final SbtRepository sbts;
    private final AirwayEventRepository airwayEvents;

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

        OffsetDateTime episodeSince = OffsetDateTime.now(ZoneOffset.UTC).minusDays(EPISODE_WINDOW_DAYS);
        List<Object[]> closed = patients.findClosedEpisodeIntervals(episodeSince);
        double trueAlos = closed.isEmpty() ? 0.0 : round1(closed.stream()
                .mapToDouble(row -> Duration.between(
                        (OffsetDateTime) row[1], (OffsetDateTime) row[2]).toMinutes() / (60.0 * 24.0))
                .average().orElse(0.0));
        double bedTurnover = totalBeds == 0 ? 0.0 : round2((double) closed.size() / totalBeds);

        return new ExecutiveDashboardResponse(
                occupancyRatePercent,
                alertFrequency,
                assetUtilization.maintenance(),
                assetUtilization,
                trueAlos,
                averageStayDays(),
                bedTurnover,
                buildMortality(closed),
                buildReadmissions(episodeSince, closed.size()));
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

    /**
     * Episode mortality over the closed set. The weaning-failure cohort is the
     * union of patients with ≥1 failed SBT and patients re-intubated within
     * 48 h of an extubation, intersected with the windowed closed episodes.
     */
    private MortalityStats buildMortality(List<Object[]> closed) {
        long deceased = closed.stream().filter(r -> r[3] == Disposition.DECEASED).count();
        long withdrawal = closed.stream().filter(r -> r[3] == Disposition.WITHDRAWAL_OF_CARE).count();
        double mortalityPercent = closed.isEmpty()
                ? 0.0 : round2((deceased + withdrawal) * 100.0 / closed.size());

        Set<UUID> weaningFailureIds = new HashSet<>(sbts.findPatientIdsWithFailedSbt());
        weaningFailureIds.addAll(reintubatedWithin48h());

        Set<UUID> closedIds = closed.stream().map(r -> (UUID) r[0]).collect(Collectors.toSet());
        Set<UUID> cohort = weaningFailureIds.stream()
                .filter(closedIds::contains).collect(Collectors.toSet());
        long cohortDeceased = closed.stream()
                .filter(r -> cohort.contains((UUID) r[0]) && r[3] == Disposition.DECEASED)
                .count();
        double cohortMortalityPercent = cohort.isEmpty()
                ? 0.0 : round2(cohortDeceased * 100.0 / cohort.size());

        return new MortalityStats(closed.size(), deceased, withdrawal, mortalityPercent,
                cohort.size(), cohortDeceased, cohortMortalityPercent);
    }

    /**
     * Patients with an EXTUBATION followed by an INTUBATION within 48 h —
     * folded from the ordered event log, mirroring AnalyticsService's
     * extubation-outcome fold.
     */
    private Set<UUID> reintubatedWithin48h() {
        Set<UUID> result = new HashSet<>();
        UUID currentPatient = null;
        OffsetDateTime lastExtubation = null;
        for (AirwayEventJpaEntity event : airwayEvents.findAllByOrderByPatientIdAscEventTimeAsc()) {
            if (!event.getPatientId().equals(currentPatient)) {
                currentPatient = event.getPatientId();
                lastExtubation = null;
            }
            if (event.getEventType() == AirwayEventType.EXTUBATION) {
                lastExtubation = event.getEventTime();
            } else if (event.getEventType() == AirwayEventType.INTUBATION
                    && lastExtubation != null
                    && Duration.between(lastExtubation, event.getEventTime()).toHours() <= 48) {
                result.add(currentPatient);
            }
        }
        return result;
    }

    private ReadmissionStats buildReadmissions(OffsetDateTime since, long closedCount) {
        long within48h = patients.countReadmissionsWithinHours(since, 48);
        long within7d = patients.countReadmissionsWithinHours(since, 168);
        double rate48h = closedCount == 0 ? 0.0 : round2(within48h * 100.0 / closedCount);
        double rate7d = closedCount == 0 ? 0.0 : round2(within7d * 100.0 / closedCount);
        return new ReadmissionStats(within48h, within7d, rate48h, rate7d);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
