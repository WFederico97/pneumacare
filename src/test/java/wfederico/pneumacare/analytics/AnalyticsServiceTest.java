package wfederico.pneumacare.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import wfederico.pneumacare.analytics.application.AnalyticsService;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private IcuBedRepository beds;
    private EvaluationRepository evaluations;
    private SbtRepository sbts;
    private MedicalShiftRepository shifts;
    private PatientRepository patients;
    private UserRepository users;
    private AirwayEventRepository airwayEvents;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        beds = mock(IcuBedRepository.class);
        evaluations = mock(EvaluationRepository.class);
        sbts = mock(SbtRepository.class);
        shifts = mock(MedicalShiftRepository.class);
        patients = mock(PatientRepository.class);
        users = mock(UserRepository.class);
        airwayEvents = mock(AirwayEventRepository.class);
        service = new AnalyticsService(beds, evaluations, sbts, shifts, patients, users, airwayEvents);

        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(14L);
        when(beds.countByStatus(BedStatus.AVAILABLE)).thenReturn(2L);
        when(sbts.countByToleranceResultAndCreatedAtAfter(any(), any())).thenReturn(0L);
        when(sbts.countByToleranceResultAndCreatedAtAfter(eq(ToleranceResult.SUCCESS), any())).thenReturn(5L);
        when(evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(any(), any())).thenReturn(0L);
        when(evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(eq(RsbiInterpretation.FAVORABLE), any())).thenReturn(7L);
        when(evaluations.countDailySince(any())).thenReturn(List.of());
        when(evaluations.countHighDrivingPressurePatients(any())).thenReturn(3L);
        when(patients.countByRespiratoryStatus(RespiratoryStatus.INTUBATED)).thenReturn(4L);
        when(patients.findIdsByRespiratoryStatus(RespiratoryStatus.INTUBATED)).thenReturn(List.of());
        when(sbts.countAttemptsByPatientSince(any())).thenReturn(List.of());
        when(airwayEvents.findAllByOrderByPatientIdAscEventTimeAsc()).thenReturn(List.of());
        when(shifts.existsByStatus(ShiftStatus.OPEN)).thenReturn(true);
        when(shifts.countByStartTimeAfter(any())).thenReturn(22L);
        when(patients.countByAdmissionDateAfter(any())).thenReturn(11L);
        when(users.count()).thenReturn(9L);
        when(users.countByEnabled(true)).thenReturn(8L);
        when(users.countByRole()).thenReturn(List.of());
    }

    private Authentication auth(String... roles) {
        var authorities = java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken("u", null, authorities);
    }

    @Test
    void therapist_getsClinicalOnly() {
        AnalyticsSummaryResponse r = service.summarize(auth("ROLE_THERAPIST"));
        assertThat(r.clinical()).isNotNull();
        assertThat(r.clinical().occupancy().total()).isEqualTo(16L);
        assertThat(r.clinical().occupancy().occupied()).isEqualTo(14L);
        assertThat(r.clinical().weaning().sbtSuccess()).isEqualTo(5L);
        assertThat(r.clinical().weaning().rsbiFavorable()).isEqualTo(7L);
        assertThat(r.clinical().lungProtection().highDrivingPressurePatients()).isEqualTo(3L);
        assertThat(r.clinical().ventilation().currentlyIntubated()).isEqualTo(4L);
        assertThat(r.clinical().ventilation().intubationDaysInWindow()).isEqualTo(0.0);
        assertThat(r.ward()).isNull();
        assertThat(r.iam()).isNull();
    }

    @Test
    void ventilatorDays_foldsIntubationIntervalsWithinWindow() {
        java.util.UUID patientA = java.util.UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        // Intubated 3 days ago, extubated 1 day ago → a 2-day interval, all in-window.
        when(airwayEvents.findAllByOrderByPatientIdAscEventTimeAsc()).thenReturn(List.of(
                airwayEvent(patientA, AirwayEventType.INTUBATION, now.minusDays(3)),
                airwayEvent(patientA, AirwayEventType.EXTUBATION, now.minusDays(1))));

        var r = service.summarize(auth("ROLE_THERAPIST"));

        assertThat(r.clinical().ventilation().intubationDaysInWindow()).isEqualTo(2.0);
    }

    @Test
    void extubation_countsReintubationsWithin48h() {
        java.util.UUID pFail = java.util.UUID.randomUUID(); // extubated then reintubated 12h later → failure
        java.util.UUID pOk = java.util.UUID.randomUUID();   // extubated, no reintubation → success
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        when(airwayEvents.findAllByOrderByPatientIdAscEventTimeAsc()).thenReturn(List.of(
                airwayEvent(pFail, AirwayEventType.EXTUBATION, now.minusDays(3)),
                airwayEvent(pFail, AirwayEventType.INTUBATION, now.minusDays(3).plusHours(12)),
                airwayEvent(pOk, AirwayEventType.EXTUBATION, now.minusDays(2))));

        var ext = service.summarize(auth("ROLE_THERAPIST")).clinical().extubation();

        assertThat(ext.extubations()).isEqualTo(2L);
        assertThat(ext.reintubations48h()).isEqualTo(1L);
        assertThat(ext.successRatePercent()).isEqualTo(50.0);
    }

    @Test
    void weaningClassification_bucketsBySbtAttemptCount() {
        java.util.UUID p1 = java.util.UUID.randomUUID(); // 0 attempts → noAttempt
        java.util.UUID p2 = java.util.UUID.randomUUID(); // 1 → simple
        java.util.UUID p3 = java.util.UUID.randomUUID(); // 2 → difficult
        java.util.UUID p4 = java.util.UUID.randomUUID(); // 3 → difficult
        java.util.UUID p5 = java.util.UUID.randomUUID(); // 4 → prolonged
        when(patients.findIdsByRespiratoryStatus(RespiratoryStatus.INTUBATED))
                .thenReturn(List.of(p1, p2, p3, p4, p5));
        when(sbts.countAttemptsByPatientSince(any())).thenReturn(List.of(
                sbtCount(p2, 1), sbtCount(p3, 2), sbtCount(p4, 3), sbtCount(p5, 4)));

        var wind = service.summarize(auth("ROLE_THERAPIST")).clinical().weaningClassification();

        assertThat(wind.noAttempt()).isEqualTo(1L);
        assertThat(wind.simple()).isEqualTo(1L);
        assertThat(wind.difficult()).isEqualTo(2L);
        assertThat(wind.prolonged()).isEqualTo(1L);
    }

    private static SbtRepository.PatientSbtCount sbtCount(java.util.UUID patientId, long total) {
        return new SbtRepository.PatientSbtCount() {
            @Override public java.util.UUID getPatientId() { return patientId; }
            @Override public long getTotal() { return total; }
        };
    }

    private static AirwayEventJpaEntity airwayEvent(java.util.UUID patientId, AirwayEventType type, OffsetDateTime at) {
        AirwayEventJpaEntity e = new AirwayEventJpaEntity();
        e.setPatientId(patientId);
        e.setEventType(type);
        e.setEventTime(at);
        return e;
    }

    @Test
    void chief_getsClinicalAndWard() {
        AnalyticsSummaryResponse r = service.summarize(auth("ROLE_CHIEF_OF_GUARD"));
        assertThat(r.ward()).isNotNull();
        assertThat(r.ward().activeShiftOpen()).isTrue();
        assertThat(r.ward().shiftsInWindow()).isEqualTo(22L);
        assertThat(r.iam()).isNull();
    }

    @Test
    void admin_getsAllSections() {
        AnalyticsSummaryResponse r = service.summarize(auth("ROLE_ADMIN"));
        assertThat(r.ward()).isNotNull();
        assertThat(r.iam()).isNotNull();
        assertThat(r.iam().totalUsers()).isEqualTo(9L);
        assertThat(r.iam().disabledUsers()).isEqualTo(1L);
    }
}
