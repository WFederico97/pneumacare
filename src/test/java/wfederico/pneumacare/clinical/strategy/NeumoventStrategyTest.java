package wfederico.pneumacare.clinical.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import wfederico.pneumacare.clinical.application.strategy.NeumoventStrategy;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.domain.input.VentilatorReading;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class NeumoventStrategyTest {

    // ── Reading: rr=20, tv=0.5L, pao2=400, fio2=1.0, pplat=20, peep=5 ───────
    //   Neumovent-specific: tidalVolume is in LITRES (not mL as in TECME)
    //   RSBI  = 20 / 0.5 L                  = 40.0       → FAVORABLE
    //   PaFi  = 400 / 1.0                   = 400.0      → NORMAL
    //   Cstat = (0.5 L × 1000) / (20 - 5)  = 33.333...  → LOW
    private static final VentilatorReading NEUMOVENT_READING =
            new VentilatorReading(20.0, 0.5, 400.0, 1.0, 20.0, 5.0);

    private NeumoventStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new NeumoventStrategy();
    }

    // ── Structure ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluate() — returns result with all three non-null sub-results")
    void evaluate_neumoventReading_returnsAllThreeSubResults() {
        VentilatorEvaluationResult result = strategy.evaluate(NEUMOVENT_READING);

        assertThat(result.rsbi()).isNotNull();
        assertThat(result.pafi()).isNotNull();
        assertThat(result.cstat()).isNotNull();
    }

    // ── Performance ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluate() — completes within 50ms performance budget")
    void evaluate_neumoventReading_completesWithinPerformanceBudget() {
        assertTimeout(Duration.ofMillis(50),
                () -> strategy.evaluate(NEUMOVENT_READING));
    }

    // ── RSBI ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluate() — RSBI uses tidal volume in litres directly (no mL conversion)")
    void evaluate_neumoventReading_rsbiUsesTidalVolumeInLitersDirectly() {
        // Neumovent: 20 / 0.5 L = 40.0  (no division by 1000 — already in L)
        VentilatorEvaluationResult result = strategy.evaluate(NEUMOVENT_READING);

        assertThat(result.rsbi().value()).isCloseTo(40.0, within(1e-9));
    }

    // ── PaFi ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluate() — PaFi passes pao2 and fio2 directly to math engine")
    void evaluate_neumoventReading_pafiMatchesMathEngine() {
        // 400 / 1.0 = 400.0
        VentilatorEvaluationResult result = strategy.evaluate(NEUMOVENT_READING);

        assertThat(result.pafi().value()).isCloseTo(400.0, within(1e-9));
    }

    // ── Cstat ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluate() — Cstat converts tidal volume from litres to mL before calculation")
    void evaluate_neumoventReading_cstatConvertsTidalVolumeFromLitresToMl() {
        // Neumovent: (0.5 L × 1000) / (20 - 5) = 500 / 15 = 33.333...
        double expected = (0.5 * 1000.0) / (20.0 - 5.0);
        VentilatorEvaluationResult result = strategy.evaluate(NEUMOVENT_READING);

        assertThat(result.cstat().value()).isCloseTo(expected, within(1e-9));
    }

    // ── Interpretations ───────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluate() — interpretations match enum from() for each computed value")
    void evaluate_neumoventReading_interpretationsMatchEnums() {
        VentilatorEvaluationResult result = strategy.evaluate(NEUMOVENT_READING);

        assertThat(result.rsbi().interpretation()).isEqualTo(RsbiInterpretation.FAVORABLE);
        assertThat(result.pafi().classification()).isEqualTo(PafiClassification.NORMAL);
        assertThat(result.cstat().interpretation()).isEqualTo(CstatInterpretation.LOW);
    }

    // ── Validation / Exception propagation ───────────────────────────────────

    @Test
    @DisplayName("evaluate() — pplat equal to peepTotal propagates IllegalArgumentException from math engine")
    void evaluate_pplatEqualsPeep_propagatesIllegalArgumentException() {
        VentilatorReading invalidReading =
                new VentilatorReading(20.0, 0.5, 400.0, 1.0, 10.0, 10.0);

        assertThatThrownBy(() -> strategy.evaluate(invalidReading))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
