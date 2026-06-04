package wfederico.pneumacare.clinical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import wfederico.pneumacare.clinical.application.ClinicalMathEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClinicalMathEngineTest {
    // ── RSBI ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RSBI — normal inputs return correct division result")
    void calculateRsbi_normalInputs_returnsCorrectValue() {
        double result = ClinicalMathEngine.calculateRsbi(22, 0.5);

        assertThat(result).isEqualTo(44.0);
    }

    @Test
    @DisplayName("RSBI — boundary value exactly at 80")
    void calculateRsbi_boundaryAt80_returnsCorrectValue() {
        double result = ClinicalMathEngine.calculateRsbi(80, 1.0);

        assertThat(result).isEqualTo(80.0);
    }

    @Test
    @DisplayName("RSBI — boundary value exactly at 105")
    void calculateRsbi_boundaryAt105_returnsCorrectValue() {
        double result = ClinicalMathEngine.calculateRsbi(105, 1.0);

        assertThat(result).isEqualTo(105.0);
    }

    // ── PaFi ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PaFi — normal inputs return correct division result")
    void calculatePafi_normalInputs_returnsCorrectValue() {
        double result = ClinicalMathEngine.calculatePafi(80, 0.4);

        assertThat(result).isEqualTo(200.0);
    }

    @Test
    @DisplayName("PaFi — boundary value exactly at 300")
    void calculatePafi_boundaryAt300_returnsCorrectValue() {
        double result = ClinicalMathEngine.calculatePafi(300, 1.0);

        assertThat(result).isEqualTo(300.0);
    }

    // ── Cstat ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cstat — normal inputs return correct value")
    void calculateCstat_normalInputs_returnsCorrectValue() {
        // vc=500, pplat=25, peep=5 → 500 / (25-5) = 25.0
        double result = ClinicalMathEngine.calculateCstat(500, 25, 5);

        assertThat(result).isEqualTo(25.0);
    }

    @Test
    @DisplayName("Cstat — pplat equal to peepTotal throws IllegalArgumentException")
    void calculateCstat_pplatEqualsPeep_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ClinicalMathEngine.calculateCstat(500, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Cstat — pplat less than peepTotal throws IllegalArgumentException")
    void calculateCstat_pplatLessThanPeep_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ClinicalMathEngine.calculateCstat(500, 5, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
