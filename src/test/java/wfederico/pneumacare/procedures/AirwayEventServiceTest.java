package wfederico.pneumacare.procedures;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.procedures.application.ActiveShiftPort;
import wfederico.pneumacare.procedures.application.AirwayEventService;
import wfederico.pneumacare.procedures.application.PatientAirwayPort;
import wfederico.pneumacare.procedures.application.PatientAirwayPort.PatientAirwayView;
import wfederico.pneumacare.procedures.domain.AirwayEventType;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventJpaEntity;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventRepository;
import wfederico.pneumacare.procedures.web.dto.AirwayEventResponse;
import wfederico.pneumacare.shared.security.CurrentUserPort;
import wfederico.pneumacare.procedures.web.dto.CreateAirwayEventRequest;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class AirwayEventServiceTest {

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ICU_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID SHIFT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID CHIEF_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
    private static final OffsetDateTime EVENT_TIME = OffsetDateTime.now(ZoneOffset.UTC);

    @Mock
    private AirwayEventRepository airwayEventRepository;
    @Mock
    private PatientAirwayPort patientAirwayPort;
    @Mock
    private ActiveShiftPort activeShiftPort;
    @Mock
    private CurrentUserPort currentUserPort;

    @InjectMocks
    private AirwayEventService service;

    private void givenPatient(RespiratoryStatus status) {
        when(patientAirwayPort.findAirwayView(PATIENT_ID))
                .thenReturn(Optional.of(new PatientAirwayView(PATIENT_ID, ICU_ID, status, true)));
    }

    private void givenOpenShift() {
        when(activeShiftPort.findActiveShiftId(ICU_ID)).thenReturn(Optional.of(SHIFT_ID));
    }

    private void echoSavedEvent() {
        when(airwayEventRepository.save(any(AirwayEventJpaEntity.class)))
                .thenAnswer(inv -> {
                    AirwayEventJpaEntity e = inv.getArgument(0);
                    e.setId(EVENT_ID);
                    return e;
                });
    }

    private CreateAirwayEventRequest request(AirwayEventType type) {
        return new CreateAirwayEventRequest(PATIENT_ID, type, EVENT_TIME);
    }

    @Test
    @DisplayName("intubation from SPONTANEOUS persists the event and advances status to INTUBATED")
    void register_validIntubation_advancesToIntubated() {
        givenPatient(RespiratoryStatus.SPONTANEOUS);
        givenOpenShift();
        when(currentUserPort.currentUserId()).thenReturn(CHIEF_ID);
        echoSavedEvent();

        AirwayEventResponse response = service.register(request(AirwayEventType.INTUBATION));

        assertThat(response.id()).isEqualTo(EVENT_ID);
        assertThat(response.patientId()).isEqualTo(PATIENT_ID);
        assertThat(response.shiftId()).isEqualTo(SHIFT_ID);
        assertThat(response.eventType()).isEqualTo(AirwayEventType.INTUBATION);
        assertThat(response.resultingStatus()).isEqualTo(RespiratoryStatus.INTUBATED);
        assertThat(response.createdBy()).isEqualTo(CHIEF_ID);

        ArgumentCaptor<AirwayEventJpaEntity> captor = ArgumentCaptor.forClass(AirwayEventJpaEntity.class);
        verify(airwayEventRepository).save(captor.capture());
        AirwayEventJpaEntity persisted = captor.getValue();
        assertThat(persisted.getShiftId()).isEqualTo(SHIFT_ID);
        assertThat(persisted.getEventTime()).isEqualTo(EVENT_TIME);
        assertThat(persisted.getCreatedBy()).isEqualTo(CHIEF_ID);

        verify(patientAirwayPort).applyRespiratoryStatus(PATIENT_ID, RespiratoryStatus.INTUBATED);
    }

    @Test
    @DisplayName("extubation from INTUBATED advances status to SPONTANEOUS")
    void register_validExtubation_advancesToSpontaneous() {
        givenPatient(RespiratoryStatus.INTUBATED);
        givenOpenShift();
        when(currentUserPort.currentUserId()).thenReturn(CHIEF_ID);
        echoSavedEvent();

        AirwayEventResponse response = service.register(request(AirwayEventType.EXTUBATION));

        assertThat(response.resultingStatus()).isEqualTo(RespiratoryStatus.SPONTANEOUS);
        verify(patientAirwayPort).applyRespiratoryStatus(PATIENT_ID, RespiratoryStatus.SPONTANEOUS);
    }

    @Test
    @DisplayName("tracheostomy from INTUBATED advances status to TRACHEOSTOMY")
    void register_validTracheostomy_advancesToTracheostomy() {
        givenPatient(RespiratoryStatus.INTUBATED);
        givenOpenShift();
        when(currentUserPort.currentUserId()).thenReturn(CHIEF_ID);
        echoSavedEvent();

        AirwayEventResponse response = service.register(request(AirwayEventType.TRACHEOSTOMY));

        assertThat(response.resultingStatus()).isEqualTo(RespiratoryStatus.TRACHEOSTOMY);
        verify(patientAirwayPort).applyRespiratoryStatus(PATIENT_ID, RespiratoryStatus.TRACHEOSTOMY);
    }

    @Test
    @DisplayName("decannulation from TRACHEOSTOMY returns the patient to SPONTANEOUS")
    void register_validDecannulation_returnsToSpontaneous() {
        givenPatient(RespiratoryStatus.TRACHEOSTOMY);
        givenOpenShift();
        when(currentUserPort.currentUserId()).thenReturn(CHIEF_ID);
        echoSavedEvent();

        AirwayEventResponse response = service.register(request(AirwayEventType.DECANNULATION));

        assertThat(response.resultingStatus()).isEqualTo(RespiratoryStatus.SPONTANEOUS);
        verify(patientAirwayPort).applyRespiratoryStatus(PATIENT_ID, RespiratoryStatus.SPONTANEOUS);
    }

    @Test
    @DisplayName("decannulation is illegal from INTUBATED (only TRACHEOSTOMY may be decannulated)")
    void register_decannulationFromIntubated_throws409() {
        givenPatient(RespiratoryStatus.INTUBATED);
        givenOpenShift();

        assertThatThrownBy(() -> service.register(request(AirwayEventType.DECANNULATION)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(airwayEventRepository, never()).save(any());
        verify(patientAirwayPort, never()).applyRespiratoryStatus(any(), any());
    }

    @Test
    @DisplayName("closed episode throws 409 before any shift lookup or write")
    void register_closedEpisode_throws409AndWritesNothing() {
        when(patientAirwayPort.findAirwayView(PATIENT_ID)).thenReturn(Optional.of(
                new PatientAirwayView(PATIENT_ID, ICU_ID, RespiratoryStatus.SPONTANEOUS, false)));

        assertThatThrownBy(() -> service.register(request(AirwayEventType.INTUBATION)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(activeShiftPort, never()).findActiveShiftId(any());
        verify(airwayEventRepository, never()).save(any());
        verify(patientAirwayPort, never()).applyRespiratoryStatus(any(), any());
    }

    @Test
    @DisplayName("illegal transition throws 409 and writes nothing, leaving status unchanged")
    void register_illegalTransition_throws409AndWritesNothing() {
        givenPatient(RespiratoryStatus.INTUBATED);
        givenOpenShift();

        assertThatThrownBy(() -> service.register(request(AirwayEventType.INTUBATION)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(airwayEventRepository, never()).save(any());
        verify(patientAirwayPort, never()).applyRespiratoryStatus(any(), any());
    }

    @Test
    @DisplayName("unknown patient throws 404 and never checks the shift")
    void register_unknownPatient_throws404() {
        when(patientAirwayPort.findAirwayView(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request(AirwayEventType.INTUBATION)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(activeShiftPort, never()).findActiveShiftId(any());
        verify(airwayEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("no OPEN shift for the patient's ICU throws 409 and writes nothing")
    void register_noOpenShift_throws409() {
        givenPatient(RespiratoryStatus.SPONTANEOUS);
        when(activeShiftPort.findActiveShiftId(ICU_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request(AirwayEventType.INTUBATION)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(airwayEventRepository, never()).save(any());
        verify(patientAirwayPort, never()).applyRespiratoryStatus(any(), any());
    }

    @Test
    @DisplayName("listing returns events mapped newest-first with derived resulting status")
    void getPatientAirwayEvents_returnsMappedEvents() {
        when(patientAirwayPort.findAirwayView(PATIENT_ID))
                .thenReturn(Optional.of(new PatientAirwayView(PATIENT_ID, ICU_ID, RespiratoryStatus.INTUBATED, true)));

        AirwayEventJpaEntity intubation = AirwayEventJpaEntity.builder()
                .id(EVENT_ID).patientId(PATIENT_ID).shiftId(SHIFT_ID)
                .eventType(AirwayEventType.INTUBATION).eventTime(EVENT_TIME).createdBy(CHIEF_ID)
                .build();
        when(airwayEventRepository.findByPatientIdOrderByEventTimeDesc(PATIENT_ID))
                .thenReturn(List.of(intubation));

        List<AirwayEventResponse> result = service.getPatientAirwayEvents(PATIENT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventType()).isEqualTo(AirwayEventType.INTUBATION);
        assertThat(result.get(0).resultingStatus()).isEqualTo(RespiratoryStatus.INTUBATED);
    }

    @Test
    @DisplayName("listing for an unknown patient throws 404")
    void getPatientAirwayEvents_unknownPatient_throws404() {
        when(patientAirwayPort.findAirwayView(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPatientAirwayEvents(PATIENT_ID))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(airwayEventRepository, never()).findByPatientIdOrderByEventTimeDesc(any());
    }
}
