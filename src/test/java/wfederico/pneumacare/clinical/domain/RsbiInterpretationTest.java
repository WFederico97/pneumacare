package wfederico.pneumacare.clinical.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RsbiInterpretationTest {

    // ── FAVORABLE branch ──────────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value strictly below 80 returns FAVORABLE")
    void from_below80_returnsFavorable() {
        assertThat(RsbiInterpretation.from(79.99))
                .isEqualTo(RsbiInterpretation.FAVORABLE);
    }

    // ── BORDERLINE branch ─────────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value exactly at 80 returns BORDERLINE (lower boundary, inclusive)")
    void from_exactlyAt80_returnsBorderline() {
        assertThat(RsbiInterpretation.from(80.0))
                .isEqualTo(RsbiInterpretation.BORDERLINE);
    }

    @Test
    @DisplayName("from() — value exactly at 105 returns BORDERLINE (upper boundary, inclusive)")
    void from_exactlyAt105_returnsBorderline() {
        assertThat(RsbiInterpretation.from(105.0))
                .isEqualTo(RsbiInterpretation.BORDERLINE);
    }

    // ── UNFAVORABLE branch ────────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value strictly above 105 returns UNFAVORABLE")
    void from_above105_returnsUnfavorable() {
        assertThat(RsbiInterpretation.from(105.01))
                .isEqualTo(RsbiInterpretation.UNFAVORABLE);
    }
}
