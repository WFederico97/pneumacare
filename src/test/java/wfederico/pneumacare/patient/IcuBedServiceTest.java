package wfederico.pneumacare.patient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import wfederico.pneumacare.patient.application.IcuBedService;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.patient.web.dto.CreateIcuBedRequest;
import wfederico.pneumacare.patient.web.dto.IcuBedResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IcuBedServiceTest {

    private static final UUID ICU_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID BED_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @Mock
    private IcuBedRepository icuBedRepository;

    @Mock
    private IcuRepository icuRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private Environment environment;

    @InjectMocks
    private IcuBedService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setDefaults() {
        ReflectionTestUtils.setField(service, "devDefaultIcuId", ICU_ID.toString());
        lenient().when(environment.matchesProfiles("dev")).thenReturn(false);
        lenient().when(patientRepository.findByBed_IdAndClinicalStatus(any(UUID.class), eq(ClinicalStatus.ADMITTED)))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    @DisplayName("findBedsForAuthenticatedIcu_validJwt_returnsMappedDashboardBeds")
    void findBedsForAuthenticatedIcu_validJwt_returnsMappedDashboardBeds() {
        setJwtAuthWithIcuId(ICU_ID.toString());

        List<IcuBedJpaEntity> beds = List.of(
                IcuBedJpaEntity.builder().id(BED_ID).bedNumber("BED-001").status(BedStatus.AVAILABLE).build(),
                IcuBedJpaEntity.builder().id(UUID.fromString("dddddddd-0000-0000-0000-000000000002")).bedNumber("BED-002").status(BedStatus.OCCUPIED).build()
        );
        when(icuBedRepository.findByIcu_IdAndStatusInOrderByBedNumberAsc(
                eq(ICU_ID), eq(List.of(BedStatus.AVAILABLE, BedStatus.OCCUPIED))))
                .thenReturn(beds);

        List<IcuBedResponse> result = service.findBedsForAuthenticatedIcu();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).bedId()).isEqualTo(BED_ID);
        assertThat(result.get(0).bedNumber()).isEqualTo("BED-001");
        assertThat(result.get(0).status()).isEqualTo(BedStatus.AVAILABLE);
        assertThat(result.get(1).bedNumber()).isEqualTo("BED-002");
        assertThat(result.get(1).status()).isEqualTo(BedStatus.OCCUPIED);
        verify(icuBedRepository).findByIcu_IdAndStatusInOrderByBedNumberAsc(
                eq(ICU_ID), eq(List.of(BedStatus.AVAILABLE, BedStatus.OCCUPIED)));
    }

    @Test
    @DisplayName("findBedsForAuthenticatedIcu_newIcuWithNoBeds_returnsEmptyList")
    void findBedsForAuthenticatedIcu_newIcuWithNoBeds_returnsEmptyList() {
        setJwtAuthWithIcuId(ICU_ID.toString());
        when(icuBedRepository.findByIcu_IdAndStatusInOrderByBedNumberAsc(
                eq(ICU_ID), eq(List.of(BedStatus.AVAILABLE, BedStatus.OCCUPIED))))
                .thenReturn(List.of());

        assertThat(service.findBedsForAuthenticatedIcu()).isEmpty();
    }

    @Test
    @DisplayName("findBedsForAuthenticatedIcu_noAuthenticationOutsideDev_throwsBusinessException401")
    void findBedsForAuthenticatedIcu_noAuthenticationOutsideDev_throwsBusinessException401() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(service::findBedsForAuthenticatedIcu)
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("findBedsForAuthenticatedIcu_noAuthenticationInDev_usesFallbackIcu")
    void findBedsForAuthenticatedIcu_noAuthenticationInDev_usesFallbackIcu() {
        SecurityContextHolder.clearContext();
        when(environment.matchesProfiles("dev")).thenReturn(true);
        when(icuBedRepository.findByIcu_IdAndStatusInOrderByBedNumberAsc(
                eq(ICU_ID), eq(List.of(BedStatus.AVAILABLE, BedStatus.OCCUPIED))))
                .thenReturn(List.of());

        assertThat(service.findBedsForAuthenticatedIcu()).isEmpty();
        verify(icuBedRepository).findByIcu_IdAndStatusInOrderByBedNumberAsc(
                eq(ICU_ID), eq(List.of(BedStatus.AVAILABLE, BedStatus.OCCUPIED)));
    }

    @Test
    @DisplayName("findBedsForAuthenticatedIcu_missingIcuClaim_throwsBusinessException400")
    void findBedsForAuthenticatedIcu_missingIcuClaim_throwsBusinessException400() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "550e8400-e29b-41d4-a716-446655440000")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));

        assertThatThrownBy(service::findBedsForAuthenticatedIcu)
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("create_validRequest_returnsCreatedBed")
    void create_validRequest_returnsCreatedBed() {
        setJwtAuthWithIcuId(ICU_ID.toString());
        CreateIcuBedRequest request = new CreateIcuBedRequest("BED-004");
        IcuJpaEntity icu = IcuJpaEntity.builder().id(ICU_ID).build();
        IcuBedJpaEntity saved = IcuBedJpaEntity.builder()
                .id(BED_ID)
                .icu(icu)
                .bedNumber("BED-004")
                .status(BedStatus.AVAILABLE)
                .build();

        when(icuRepository.findById(ICU_ID)).thenReturn(java.util.Optional.of(icu));
        when(icuBedRepository.save(any(IcuBedJpaEntity.class))).thenReturn(saved);

        IcuBedResponse response = service.create(request);

        assertThat(response.bedId()).isEqualTo(BED_ID);
        assertThat(response.bedNumber()).isEqualTo("BED-004");
        assertThat(response.status()).isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    @DisplayName("create_unknownIcu_throwsBusinessException404")
    void create_unknownIcu_throwsBusinessException404() {
        setJwtAuthWithIcuId(ICU_ID.toString());
        CreateIcuBedRequest request = new CreateIcuBedRequest("BED-004");
        when(icuRepository.findById(ICU_ID)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("create_duplicateBedNumber_throwsConflict409")
    void create_duplicateBedNumber_throwsConflict409() {
        setJwtAuthWithIcuId(ICU_ID.toString());
        CreateIcuBedRequest request = new CreateIcuBedRequest("  BED-004 ");
        when(icuBedRepository.existsByIcu_IdAndBedNumberIgnoreCase(ICU_ID, "BED-004")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    private void setJwtAuthWithIcuId(String icuIdClaim) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "550e8400-e29b-41d4-a716-446655440000")
                .claim("icu_id", icuIdClaim)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
