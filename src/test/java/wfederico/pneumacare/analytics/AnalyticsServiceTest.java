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
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.procedures.domain.ToleranceResult;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;
import wfederico.pneumacare.shared.security.user.UserRepository;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;

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
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        beds = mock(IcuBedRepository.class);
        evaluations = mock(EvaluationRepository.class);
        sbts = mock(SbtRepository.class);
        shifts = mock(MedicalShiftRepository.class);
        patients = mock(PatientRepository.class);
        users = mock(UserRepository.class);
        service = new AnalyticsService(beds, evaluations, sbts, shifts, patients, users);

        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(14L);
        when(beds.countByStatus(BedStatus.AVAILABLE)).thenReturn(2L);
        when(beds.countByStatus(BedStatus.MAINTENANCE)).thenReturn(2L);
        when(sbts.countByToleranceResultAndCreatedAtAfter(any(), any())).thenReturn(0L);
        when(sbts.countByToleranceResultAndCreatedAtAfter(eq(ToleranceResult.SUCCESS), any())).thenReturn(5L);
        when(evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(any(), any())).thenReturn(0L);
        when(evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(eq(RsbiInterpretation.FAVORABLE), any())).thenReturn(7L);
        when(evaluations.countDailySince(any())).thenReturn(List.of());
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
        assertThat(r.clinical().occupancy().total()).isEqualTo(18L);
        assertThat(r.clinical().occupancy().occupied()).isEqualTo(14L);
        assertThat(r.clinical().weaning().sbtSuccess()).isEqualTo(5L);
        assertThat(r.clinical().weaning().rsbiFavorable()).isEqualTo(7L);
        assertThat(r.ward()).isNull();
        assertThat(r.iam()).isNull();
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
