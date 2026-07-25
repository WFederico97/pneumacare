package wfederico.pneumacare.patient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import wfederico.pneumacare.inventory.application.AssetAssignmentService;
import wfederico.pneumacare.patient.application.PatientDischargeService;
import wfederico.pneumacare.patient.application.PatientDischargedEvent;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.domain.Disposition;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientDischargeServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private IcuBedRepository bedRepository;
    @Mock private AssetAssignmentService assetAssignmentService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PatientDischargeService service;

    private UUID patientId;
    private PatientJpaEntity patient;
    private IcuBedJpaEntity bed;

    @BeforeEach
    void setUp() {
        service = new PatientDischargeService(
                patientRepository, bedRepository, assetAssignmentService, eventPublisher);
        patientId = UUID.randomUUID();
        bed = IcuBedJpaEntity.builder().status(BedStatus.OCCUPIED).build();
        patient = PatientJpaEntity.builder()
                .id(patientId)
                .bed(bed)
                .clinicalStatus(ClinicalStatus.ADMITTED)
                .respiratoryStatus(RespiratoryStatus.SPONTANEOUS)
                .build();
        // admissionDate is @CreationTimestamp (not builder-populated in unit tests).
        patient.setAdmissionDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5));
    }

    @Test
    void dischargeClosesEpisodeFreesBedAndPublishesEvent() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        service.discharge(patientId, Disposition.WARD, null);

        assertThat(patient.getClinicalStatus()).isEqualTo(ClinicalStatus.DISCHARGED);
        assertThat(patient.getDisposition()).isEqualTo(Disposition.WARD);
        assertThat(patient.getDischargeDate()).isNotNull();
        assertThat(patient.getBed()).isNull();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
        verify(bedRepository).save(bed);
        verify(patientRepository).save(patient);
        verify(assetAssignmentService).releaseForPatient(patientId);
        verify(eventPublisher).publishEvent(any(PatientDischargedEvent.class));
    }

    @Test
    void dischargeExternalTransferMapsToTransferred() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        service.discharge(patientId, Disposition.TRANSFER_EXTERNAL, null);

        assertThat(patient.getClinicalStatus()).isEqualTo(ClinicalStatus.TRANSFERRED);
    }

    @Test
    void dischargeRejectsUnknownPatientWith404() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.discharge(patientId, Disposition.HOME, null))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void dischargeRejectsDoubleDischargeWith409() {
        patient.setClinicalStatus(ClinicalStatus.DISCHARGED);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.discharge(patientId, Disposition.HOME, null))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void dischargeRejectsDateBeforeAdmissionWith400() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        OffsetDateTime beforeAdmission = patient.getAdmissionDate().minusHours(1);

        assertThatThrownBy(() -> service.discharge(patientId, Disposition.HOME, beforeAdmission))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void dischargeRejectsFutureDateWith400() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        OffsetDateTime future = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);

        assertThatThrownBy(() -> service.discharge(patientId, Disposition.HOME, future))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void dischargeRejectsIntubatedPatientGoingHomeOrWard() {
        patient.setRespiratoryStatus(RespiratoryStatus.INTUBATED);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.discharge(patientId, Disposition.HOME, null))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.discharge(patientId, Disposition.WARD, null))
                .isInstanceOf(BusinessLayerException.class);
    }

    @Test
    void dischargeAllowsIntubatedPatientForDeathAndExternalTransfer() {
        patient.setRespiratoryStatus(RespiratoryStatus.INTUBATED);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        service.discharge(patientId, Disposition.DECEASED, null);

        assertThat(patient.getDisposition()).isEqualTo(Disposition.DECEASED);
    }

    @Test
    void dischargeToleratesPatientWithoutBed() {
        patient.setBed(null);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        service.discharge(patientId, Disposition.HOME, null);

        assertThat(patient.getClinicalStatus()).isEqualTo(ClinicalStatus.DISCHARGED);
    }
}
