package wfederico.pneumacare.clinical.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CstatInterpretationTest {

    // ── HIGH branch ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value exactly at 100 returns HIGH (lower boundary, inclusive)")
    void from_exactlyAt100_returnsHigh() {
        assertThat(CstatInterpretation.from(100.0))
                .isEqualTo(CstatInterpretation.HIGH);
    }

    @Test
    @DisplayName("from() — value above 100 returns HIGH")
    void from_above100_returnsHigh() {
        assertThat(CstatInterpretation.from(120.0))
                .isEqualTo(CstatInterpretation.HIGH);
    }

    // ── NORMAL branch ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value exactly at 50 returns NORMAL (lower boundary, inclusive)")
    void from_exactlyAt50_returnsNormal() {
        assertThat(CstatInterpretation.from(50.0))
                .isEqualTo(CstatInterpretation.NORMAL);
    }

    // ── LOW branch ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value strictly below 50 returns LOW")
    void from_below50_returnsLow() {
        assertThat(CstatInterpretation.from(49.99))
                .isEqualTo(CstatInterpretation.LOW);
    }
}
