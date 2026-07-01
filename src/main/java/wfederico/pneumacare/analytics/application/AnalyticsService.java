package wfederico.pneumacare.analytics.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.ClinicalAnalytics;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.IamAnalytics;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.OccupancyStats;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.TrendPoint;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.WardAnalytics;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse.WeaningStats;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.procedures.domain.ToleranceResult;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;
import wfederico.pneumacare.shared.security.user.UserRepository;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final int WINDOW_DAYS = 14;

    private final IcuBedRepository beds;
    private final EvaluationRepository evaluations;
    private final SbtRepository sbts;
    private final MedicalShiftRepository shifts;
    private final PatientRepository patients;
    private final UserRepository users;

    public AnalyticsService(IcuBedRepository beds, EvaluationRepository evaluations, SbtRepository sbts,
                            MedicalShiftRepository shifts, PatientRepository patients, UserRepository users) {
        this.beds = beds;
        this.evaluations = evaluations;
        this.sbts = sbts;
        this.shifts = shifts;
        this.patients = patients;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summarize(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(WINDOW_DAYS);

        ClinicalAnalytics clinical = buildClinical(since);
        WardAnalytics ward = (roles.contains("ROLE_CHIEF_OF_GUARD") || roles.contains("ROLE_ADMIN"))
                ? buildWard(since) : null;
        IamAnalytics iam = roles.contains("ROLE_ADMIN") ? buildIam() : null;
        return new AnalyticsSummaryResponse(clinical, ward, iam);
    }

    private ClinicalAnalytics buildClinical(OffsetDateTime since) {
        long occupied = beds.countByStatus(BedStatus.OCCUPIED);
        long available = beds.countByStatus(BedStatus.AVAILABLE);
        long maintenance = beds.countByStatus(BedStatus.MAINTENANCE);
        long total = occupied + available + maintenance;
        double rate = total == 0 ? 0.0 : (double) occupied / total;
        OccupancyStats occupancy = new OccupancyStats(total, occupied, available, maintenance, rate);

        WeaningStats weaning = new WeaningStats(
                WINDOW_DAYS,
                sbts.countByToleranceResultAndCreatedAtAfter(ToleranceResult.SUCCESS, since),
                sbts.countByToleranceResultAndCreatedAtAfter(ToleranceResult.FAILURE, since),
                evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(RsbiInterpretation.FAVORABLE, since),
                evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(RsbiInterpretation.BORDERLINE, since),
                evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(RsbiInterpretation.UNFAVORABLE, since));

        return new ClinicalAnalytics(occupancy, weaning, buildTrend(since));
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
