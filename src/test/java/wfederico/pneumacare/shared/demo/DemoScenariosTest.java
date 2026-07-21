package wfederico.pneumacare.shared.demo;

import org.junit.jupiter.api.Test;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoScenariosTest {

    @Test
    void sixPatientsEachWithThreeReadings() {
        List<DemoScenarios.Patient> patients = DemoScenarios.patients();
        assertThat(patients).hasSize(6);
        assertThat(patients).allSatisfy(p -> assertThat(p.readings()).hasSize(3));
    }

    @Test
    void allReadingsSatisfyDbCheckConstraints() {
        DemoScenarios.patients().forEach(p -> p.readings().forEach(r -> {
            assertThat(r.fio2()).isBetween(0.21, 1.0);
            assertThat(r.f()).isBetween(0.0, 80.0);
            assertThat(r.pao2()).isBetween(0.0, 700.0);
            assertThat(r.pplat()).isGreaterThan(r.peep());
        }));
    }

    @Test
    void latestReadingBandsMatchIntendedVerdictInputs() {
        // For each patient, the day-0 reading is last. Assert the three bands so the
        // private weaningVerdict() (defer/monitor/ready) is exercised deterministically.
        assertBands(0, RsbiInterpretation.UNFAVORABLE, PafiClassification.SEVERE_ARDS, CstatInterpretation.LOW);
        assertBands(1, RsbiInterpretation.BORDERLINE,  PafiClassification.MILD_ARDS,   CstatInterpretation.NORMAL);
        assertBands(2, RsbiInterpretation.FAVORABLE,   PafiClassification.NORMAL,      CstatInterpretation.NORMAL);
        assertBands(3, RsbiInterpretation.FAVORABLE,   PafiClassification.AT_RISK,     CstatInterpretation.LOW);
        assertBands(4, RsbiInterpretation.FAVORABLE,   PafiClassification.MODERATE_ARDS, CstatInterpretation.NORMAL);
        assertBands(5, RsbiInterpretation.FAVORABLE,   PafiClassification.NORMAL,      CstatInterpretation.HIGH);
    }

    private void assertBands(int patientIndex, RsbiInterpretation rsbi,
                             PafiClassification pafi, CstatInterpretation cstat) {
        List<DemoScenarios.Reading> readings = DemoScenarios.patients().get(patientIndex).readings();
        DemoScenarios.Reading r = readings.get(readings.size() - 1);
        double rsbiVal = r.f() / (r.vt() / 1000.0);
        double pafiVal = r.pao2() / r.fio2();
        double cstatVal = r.vt() / (r.pplat() - r.peep());
        assertThat(RsbiInterpretation.from(rsbiVal)).isEqualTo(rsbi);
        assertThat(PafiClassification.from(pafiVal)).isEqualTo(pafi);
        assertThat(CstatInterpretation.from(cstatVal)).isEqualTo(cstat);
    }
}
