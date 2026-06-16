package wfederico.pneumacare.procedures;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import wfederico.pneumacare.procedures.application.ActiveShiftPort;
import wfederico.pneumacare.procedures.application.PatientLookupPort;
import wfederico.pneumacare.procedures.application.SbtService;
import wfederico.pneumacare.procedures.domain.ToleranceResult;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtJpaEntity;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;
import wfederico.pneumacare.procedures.web.dto.CreateSbtRequest;
import wfederico.pneumacare.procedures.web.dto.SbtResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.CurrentUserPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SbtServiceTest {

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ICU_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID SHIFT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID CHIEF_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID SBT_ID = UUID.fromString("ffffffff-0000-0000-0000-000000000001");

    @Mock
    private SbtRepository sbtRepository;
    @Mock
    private PatientLookupPort patientLookupPort;
    @Mock
    private ActiveShiftPort activeShiftPort;
    @Mock
    private CurrentUserPort currentUserPort;

    @InjectMocks
    private SbtService service;

    private void echoSavedSbt() {
        when(sbtRepository.save(any(SbtJpaEntity.class))).thenAnswer(inv -> {
            SbtJpaEntity e = inv.getArgument(0);
            e.setId(SBT_ID);
            return e;
        });
    }

    private CreateSbtRequest request(int duration, ToleranceResult result) {
        return new CreateSbtRequest(PATIENT_ID, duration, result);
    }

    @Test
    @DisplayName("successful trial persists the SBT and returns it")
    void register_success_persists() {
        when(patientLookupPort.findIcuId(PATIENT_ID)).thenReturn(Optional.of(ICU_ID));
        when(activeShiftPort.findActiveShiftId(ICU_ID)).thenReturn(Optional.of(SHIFT_ID));
        when(currentUserPort.currentUserId()).thenReturn(CHIEF_ID);
        echoSavedSbt();

        SbtResponse response = service.register(request(30, ToleranceResult.SUCCESS));

        assertThat(response.id()).isEqualTo(SBT_ID);
        assertThat(response.patientId()).isEqualTo(PATIENT_ID);
        assertThat(response.shiftId()).isEqualTo(SHIFT_ID);
        assertThat(response.durationMinutes()).isEqualTo(30);
        assertThat(response.toleranceResult()).isEqualTo(ToleranceResult.SUCCESS);
        assertThat(response.performedBy()).isEqualTo(CHIEF_ID);

        ArgumentCaptor<SbtJpaEntity> captor = ArgumentCaptor.forClass(SbtJpaEntity.class);
        verify(sbtRepository).save(captor.capture());
        assertThat(captor.getValue().getShiftId()).isEqualTo(SHIFT_ID);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(CHIEF_ID);
    }

    @Test
    @DisplayName("failure outcome is a valid recorded trial and persists")
    void register_failure_persists() {
        when(patientLookupPort.findIcuId(PATIENT_ID)).thenReturn(Optional.of(ICU_ID));
        when(activeShiftPort.findActiveShiftId(ICU_ID)).thenReturn(Optional.of(SHIFT_ID));
        when(currentUserPort.currentUserId()).thenReturn(CHIEF_ID);
        echoSavedSbt();

        SbtResponse response = service.register(request(45, ToleranceResult.FAILURE));

        assertThat(response.toleranceResult()).isEqualTo(ToleranceResult.FAILURE);
        verify(sbtRepository).save(any(SbtJpaEntity.class));
    }

    @Test
    @DisplayName("zero duration throws 422 and writes nothing")
    void register_zeroDuration_throws422() {
        assertThatThrownBy(() -> service.register(request(0, ToleranceResult.SUCCESS)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        verify(patientLookupPort, never()).findIcuId(any());
        verify(sbtRepository, never()).save(any());
    }

    @Test
    @DisplayName("negative duration throws 422 and writes nothing")
    void register_negativeDuration_throws422() {
        assertThatThrownBy(() -> service.register(request(-5, ToleranceResult.SUCCESS)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        verify(sbtRepository, never()).save(any());
    }

    @Test
    @DisplayName("unknown patient throws 404 and never checks the shift")
    void register_unknownPatient_throws404() {
        when(patientLookupPort.findIcuId(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request(30, ToleranceResult.SUCCESS)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(activeShiftPort, never()).findActiveShiftId(any());
        verify(sbtRepository, never()).save(any());
    }

    @Test
    @DisplayName("no OPEN shift for the patient's ICU throws 409 and writes nothing")
    void register_noOpenShift_throws409() {
        when(patientLookupPort.findIcuId(PATIENT_ID)).thenReturn(Optional.of(ICU_ID));
        when(activeShiftPort.findActiveShiftId(ICU_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request(30, ToleranceResult.SUCCESS)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(sbtRepository, never()).save(any());
    }

    @Test
    @DisplayName("history returns the patient's trials mapped newest-first")
    void getHistory_returnsMappedTrials() {
        when(patientLookupPort.findIcuId(PATIENT_ID)).thenReturn(Optional.of(ICU_ID));
        SbtJpaEntity sbt = SbtJpaEntity.builder()
                .id(SBT_ID).patientId(PATIENT_ID).shiftId(SHIFT_ID)
                .durationMinutes(30).toleranceResult(ToleranceResult.SUCCESS).createdBy(CHIEF_ID)
                .build();
        when(sbtRepository.findByPatientIdOrderByCreatedAtDesc(PATIENT_ID)).thenReturn(List.of(sbt));

        List<SbtResponse> result = service.getHistory(PATIENT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(SBT_ID);
        assertThat(result.get(0).toleranceResult()).isEqualTo(ToleranceResult.SUCCESS);
    }

    @Test
    @DisplayName("history for an unknown patient throws 404")
    void getHistory_unknownPatient_throws404() {
        when(patientLookupPort.findIcuId(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(PATIENT_ID))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(sbtRepository, never()).findByPatientIdOrderByCreatedAtDesc(any());
    }
}
