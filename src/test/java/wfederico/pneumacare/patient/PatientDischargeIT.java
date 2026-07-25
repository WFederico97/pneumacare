package wfederico.pneumacare.patient;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import wfederico.pneumacare.TestcontainersConfiguration;
import wfederico.pneumacare.analytics.application.ExecutiveAnalyticsService;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse;
import wfederico.pneumacare.patient.application.PatientDischargeService;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.domain.Disposition;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end discharge flow against Testcontainers Postgres: episode terminus,
 * bed release, double-discharge rejection, executive-metric visibility and
 * readmission representability (two episodes per identity, post-V29).
 *
 * <p>Disabled by repo convention; run individually with:
 * mvnw.cmd test -Dtest=PatientDischargeIT   (Docker required)
 */
@Disabled("Requires Docker. Run: ./mvnw -Dtest=PatientDischargeIT test")
@SpringBootTest(properties = {
        "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.docker.compose.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class PatientDischargeIT {

    @Autowired private PatientDischargeService dischargeService;
    @Autowired private ExecutiveAnalyticsService executiveAnalytics;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PatientIdentityRepository identityRepository;
    @Autowired private IcuRepository icuRepository;
    @Autowired private IcuBedRepository bedRepository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Test
    void dischargeClosesEpisodeAndFeedsExecutiveMetrics() {
        IcuJpaEntity icu = icuRepository.saveAndFlush(IcuJpaEntity.builder()
                .hospitalId(UUID.randomUUID())
                .name("UTI Discharge IT")
                .code("UTI-DIS-IT")
                .build());
        IcuBedJpaEntity bed = bedRepository.saveAndFlush(IcuBedJpaEntity.builder()
                .icu(icu)
                .bedNumber("DIS-IT-01")
                .status(BedStatus.OCCUPIED)
                .build());
        PatientIdentityJpaEntity identity = identityRepository.saveAndFlush(
                PatientIdentityJpaEntity.builder()
                        .firstName("Test").lastName("Episode")
                        .birthDate(LocalDate.of(1970, 1, 1))
                        .build());
        PatientJpaEntity patient = patientRepository.saveAndFlush(PatientJpaEntity.builder()
                .icu(icu).identity(identity).bed(bed)
                .clinicalStatus(ClinicalStatus.ADMITTED)
                .build());

        dischargeService.discharge(patient.getId(), Disposition.HOME, null);

        PatientJpaEntity closed = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(closed.getClinicalStatus()).isEqualTo(ClinicalStatus.DISCHARGED);
        assertThat(closed.getDisposition()).isEqualTo(Disposition.HOME);
        assertThat(closed.getDischargeDate()).isNotNull();
        assertThat(bedRepository.findById(bed.getId()).orElseThrow().getStatus())
                .isEqualTo(BedStatus.AVAILABLE);

        // Double discharge rejected by the DB-backed state, not just a mock.
        assertThatThrownBy(() ->
                dischargeService.discharge(patient.getId(), Disposition.HOME, null))
                .isInstanceOf(BusinessLayerException.class);

        // Executive metrics see the closed episode.
        ExecutiveDashboardResponse dashboard = executiveAnalytics.dashboard();
        assertThat(dashboard.mortality().closedEpisodes()).isGreaterThanOrEqualTo(1);
        assertThat(dashboard.averageStayDays()).isGreaterThanOrEqualTo(0.0);

        // Readmission is representable: the same identity admits again (post-V29).
        PatientJpaEntity second = patientRepository.saveAndFlush(PatientJpaEntity.builder()
                .icu(icu).identity(identity)
                .clinicalStatus(ClinicalStatus.ADMITTED)
                .build());
        assertThat(second.getId()).isNotEqualTo(patient.getId());
    }
}
