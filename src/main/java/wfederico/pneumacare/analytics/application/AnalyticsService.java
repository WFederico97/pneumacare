package wfederico.pneumacare.analytics.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.ClinicalAnalytics;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.ExtubationStats;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.IamAnalytics;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.LungProtectionStats;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.OccupancyStats;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.TrendPoint;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.VentilationStats;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.WardAnalytics;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.WeaningClassificationStats;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.WeaningStats;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.procedures.domain.AirwayEventType;
import wfederico.pneumacare.procedures.domain.ToleranceResult;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventJpaEntity;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventRepository;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;
import wfederico.pneumacare.shared.security.user.UserRepository;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only reporting service that assembles the role-scoped analytics summary.
 *
 * <p>This is a cross-context read-model: it deliberately reads repositories from
 * several bounded contexts (beds, evaluations, SBT, shifts, users) to produce a
 * single dashboard payload, and performs no writes. Sections are included
 * additively by the caller's role (clinical → ward → iam).
 */
@Service
public class AnalyticsService {

    private static final int DEFAULT_WINDOW_DAYS = 14;
    private static final int MIN_WINDOW_DAYS = 1;
    private static final int MAX_WINDOW_DAYS = 365;

    private final IcuBedRepository beds;
    private final EvaluationRepository evaluations;
    private final SbtRepository sbts;
    private final MedicalShiftRepository shifts;
    private final PatientRepository patients;
    private final UserRepository users;
    private final AirwayEventRepository airwayEvents;

    public AnalyticsService(IcuBedRepository beds, EvaluationRepository evaluations, SbtRepository sbts,
                            MedicalShiftRepository shifts, PatientRepository patients, UserRepository users,
                            AirwayEventRepository airwayEvents) {
        this.beds = beds;
        this.evaluations = evaluations;
        this.sbts = sbts;
        this.shifts = shifts;
        this.patients = patients;
        this.users = users;
        this.airwayEvents = airwayEvents;
    }

    /** Overload used by internal callers that want the default window. */
    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summarize(Authentication authentication) {
        return summarize(authentication, DEFAULT_WINDOW_DAYS);
    }

    /**
     * @param windowDays date-range filter in days; clamped to [{@value #MIN_WINDOW_DAYS},
     *                   {@value #MAX_WINDOW_DAYS}] so an out-of-range query param can never
     *                   produce an unbounded scan or a negative window.
     */
    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summarize(Authentication authentication, int windowDays) {
        int window = Math.max(MIN_WINDOW_DAYS, Math.min(MAX_WINDOW_DAYS, windowDays));
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(window);

        ClinicalAnalytics clinical = buildClinical(since, window);
        WardAnalytics ward = (roles.contains("ROLE_CHIEF_OF_GUARD") || roles.contains("ROLE_ADMIN"))
                ? buildWard(since) : null;
        IamAnalytics iam = roles.contains("ROLE_ADMIN") ? buildIam() : null;
        return new AnalyticsSummaryResponse(clinical, ward, iam);
    }

    private ClinicalAnalytics buildClinical(OffsetDateTime since, int windowDays) {
        long occupied = beds.countByStatus(BedStatus.OCCUPIED);
        long available = beds.countByStatus(BedStatus.AVAILABLE);
        long total = occupied + available;
        double rate = total == 0 ? 0.0 : (double) occupied / total;
        OccupancyStats occupancy = new OccupancyStats(total, occupied, available, rate);

        WeaningStats weaning = new WeaningStats(
                windowDays,
                sbts.countByToleranceResultAndCreatedAtAfter(ToleranceResult.SUCCESS, since),
                sbts.countByToleranceResultAndCreatedAtAfter(ToleranceResult.FAILURE, since),
                evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(RsbiInterpretation.FAVORABLE, since),
                evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(RsbiInterpretation.BORDERLINE, since),
                evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(RsbiInterpretation.UNFAVORABLE, since));

        LungProtectionStats lungProtection =
                new LungProtectionStats(evaluations.countHighDrivingPressurePatients(since));

        // One pass over the airway-event log feeds both ventilator-days and extubation outcomes.
        List<AirwayEventJpaEntity> airway = airwayEvents.findAllByOrderByPatientIdAscEventTimeAsc();

        return new ClinicalAnalytics(occupancy, weaning, buildTrend(since), lungProtection,
                buildVentilation(since, airway), buildWeaningClassification(since),
                buildExtubation(since, airway));
    }

    /**
     * Extubation outcomes in the window. For each EXTUBATION recorded since
     * {@code since}, a re-intubation of the same patient within 48 h counts as an
     * extubation failure; the success rate is the complement.
     */
    private ExtubationStats buildExtubation(OffsetDateTime since, List<AirwayEventJpaEntity> events) {
        Map<UUID, List<AirwayEventJpaEntity>> byPatient = new LinkedHashMap<>();
        for (AirwayEventJpaEntity e : events) {
            byPatient.computeIfAbsent(e.getPatientId(), k -> new ArrayList<>()).add(e);
        }

        long extubations = 0;
        long reintubations = 0;
        for (List<AirwayEventJpaEntity> patientEvents : byPatient.values()) {
            for (int i = 0; i < patientEvents.size(); i++) {
                AirwayEventJpaEntity e = patientEvents.get(i);
                if (e.getEventType() != AirwayEventType.EXTUBATION || e.getEventTime().isBefore(since)) {
                    continue;
                }
                extubations++;
                OffsetDateTime limit = e.getEventTime().plusHours(48);
                for (int j = i + 1; j < patientEvents.size(); j++) {
                    AirwayEventJpaEntity next = patientEvents.get(j);
                    if (next.getEventTime().isAfter(limit)) {
                        break;
                    }
                    if (next.getEventType() == AirwayEventType.INTUBATION) {
                        reintubations++;
                        break;
                    }
                }
            }
        }
        double successRate = extubations == 0
                ? 0.0
                : Math.round((extubations - reintubations) * 1000.0 / extubations) / 10.0;
        return new ExtubationStats(extubations, reintubations, successRate);
    }

    /**
     * WIND-aligned weaning difficulty over the currently-intubated cohort,
     * approximated by each patient's SBT attempt count within the window:
     * 0 → no attempt, 1 → simple, 2–3 → difficult, &gt; 3 → prolonged.
     */
    private WeaningClassificationStats buildWeaningClassification(OffsetDateTime since) {
        Map<UUID, Long> attempts = new HashMap<>();
        sbts.countAttemptsByPatientSince(since).forEach(c -> attempts.put(c.getPatientId(), c.getTotal()));

        long noAttempt = 0, simple = 0, difficult = 0, prolonged = 0;
        for (UUID patientId : patients.findIdsByRespiratoryStatus(RespiratoryStatus.INTUBATED)) {
            long n = attempts.getOrDefault(patientId, 0L);
            if (n == 0) {
                noAttempt++;
            } else if (n == 1) {
                simple++;
            } else if (n <= 3) {
                difficult++;
            } else {
                prolonged++;
            }
        }
        return new WeaningClassificationStats(noAttempt, simple, difficult, prolonged);
    }

    /**
     * Invasive-ventilation load. The current census reads the patient airway
     * state directly; ventilator-days are folded from the append-only airway-event
     * log: each INTUBATION opens an interval that the next EXTUBATION/TRACHEOSTOMY
     * closes (a still-open intubation is counted up to now), and each interval's
     * overlap with the reporting window is summed.
     */
    private VentilationStats buildVentilation(OffsetDateTime since, List<AirwayEventJpaEntity> events) {
        long currentlyIntubated = patients.countByRespiratoryStatus(RespiratoryStatus.INTUBATED);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        double totalDays = 0.0;
        UUID currentPatient = null;
        OffsetDateTime openStart = null;

        for (AirwayEventJpaEntity event : events) {
            if (!event.getPatientId().equals(currentPatient)) {
                // New patient: a dangling open interval means still intubated → up to now.
                if (openStart != null) {
                    totalDays += overlapDays(openStart, now, since, now);
                }
                currentPatient = event.getPatientId();
                openStart = null;
            }
            switch (event.getEventType()) {
                case INTUBATION -> openStart = event.getEventTime();
                case EXTUBATION, TRACHEOSTOMY -> {
                    if (openStart != null) {
                        totalDays += overlapDays(openStart, event.getEventTime(), since, now);
                        openStart = null;
                    }
                }
            }
        }
        if (openStart != null) {
            totalDays += overlapDays(openStart, now, since, now);
        }
        return new VentilationStats(currentlyIntubated, Math.round(totalDays * 10.0) / 10.0);
    }

    /** Days that [{@code start}, {@code end}] overlaps the window [{@code winStart}, {@code winEnd}]. */
    private static double overlapDays(OffsetDateTime start, OffsetDateTime end,
                                      OffsetDateTime winStart, OffsetDateTime winEnd) {
        OffsetDateTime s = start.isAfter(winStart) ? start : winStart;
        OffsetDateTime e = end.isBefore(winEnd) ? end : winEnd;
        if (!e.isAfter(s)) {
            return 0.0;
        }
        return Duration.between(s, e).toMinutes() / (60.0 * 24.0);
    }

    private List<TrendPoint> buildTrend(OffsetDateTime since) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        evaluations.countDailySince(since).forEach(dc -> counts.put(dc.getDay(), dc.getTotal()));
        List<TrendPoint> trend = new ArrayList<>();
        LocalDate start = since.toLocalDate();
        LocalDate today = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            trend.add(new TrendPoint(d, counts.getOrDefault(d, 0L)));
        }
        return trend;
    }

    private WardAnalytics buildWard(OffsetDateTime since) {
        return new WardAnalytics(
                shifts.existsByStatus(ShiftStatus.OPEN),
                shifts.countByStartTimeAfter(since),
                patients.countByAdmissionDateAfter(since));
    }

    private IamAnalytics buildIam() {
        long total = users.count();
        long enabled = users.countByEnabled(true);
        Map<String, Long> byRole = new LinkedHashMap<>();
        users.countByRole().forEach(rc -> byRole.put(rc.getRole().name(), rc.getTotal()));
        return new IamAnalytics(total, enabled, total - enabled, byRole);
    }
}
