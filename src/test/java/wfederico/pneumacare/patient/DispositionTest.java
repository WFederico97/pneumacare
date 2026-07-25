package wfederico.pneumacare.patient;

import org.junit.jupiter.api.Test;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.domain.Disposition;

import static org.assertj.core.api.Assertions.assertThat;

class DispositionTest {

    @Test
    void inHospitalTerminationsMapToDischarged() {
        assertThat(Disposition.HOME.toClinicalStatus()).isEqualTo(ClinicalStatus.DISCHARGED);
        assertThat(Disposition.WARD.toClinicalStatus()).isEqualTo(ClinicalStatus.DISCHARGED);
        assertThat(Disposition.DECEASED.toClinicalStatus()).isEqualTo(ClinicalStatus.DISCHARGED);
        assertThat(Disposition.WITHDRAWAL_OF_CARE.toClinicalStatus()).isEqualTo(ClinicalStatus.DISCHARGED);
    }

    @Test
    void externalTransferMapsToTransferred() {
        assertThat(Disposition.TRANSFER_EXTERNAL.toClinicalStatus()).isEqualTo(ClinicalStatus.TRANSFERRED);
    }
}
