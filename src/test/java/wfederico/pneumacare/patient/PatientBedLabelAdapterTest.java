package wfederico.pneumacare.patient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wfederico.pneumacare.patient.infrastructure.PatientBedLabelAdapter;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PatientBedLabelAdapter}. The repository is mocked; the
 * JPQL projection itself is not exercised here (that would require a
 * Testcontainers slice, which is out of scope for the unit TDD loop).
 */
@ExtendWith(MockitoExtension.class)
class PatientBedLabelAdapterTest {

    private static final UUID PATIENT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private PatientBedLabelAdapter adapter;

    @Test
    @DisplayName("findBedLabel_bedAssigned_returnsBedNumber")
    void findBedLabel_assigned() {
        when(patientRepository.findBedLabelByPatientId(PATIENT_ID))
                .thenReturn(Optional.of("Cama 3"));

        assertThat(adapter.findBedLabel(PATIENT_ID)).contains("Cama 3");
    }

    @Test
    @DisplayName("findBedLabel_noBedAssigned_returnsEmpty")
    void findBedLabel_unassigned() {
        when(patientRepository.findBedLabelByPatientId(PATIENT_ID))
                .thenReturn(Optional.empty());

        assertThat(adapter.findBedLabel(PATIENT_ID)).isEmpty();
    }
}
