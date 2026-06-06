package wfederico.pneumacare.clinical.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PafiClassificationTest {

    // ── NORMAL branch ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value exactly at 400 returns NORMAL (lower boundary, inclusive)")
    void from_exactlyAt400_returnsNormal() {
        assertThat(PafiClassification.from(400.0))
                .isEqualTo(PafiClassification.NORMAL);
    }

    @Test
    @DisplayName("from() — value above 400 returns NORMAL")
    void from_above400_returnsNormal() {
        assertThat(PafiClassification.from(450.0))
                .isEqualTo(PafiClassification.NORMAL);
    }

    // ── AT_RISK branch ────────────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value exactly at 300 returns AT_RISK (lower boundary, inclusive)")
    void from_exactlyAt300_returnsAtRisk() {
        assertThat(PafiClassification.from(300.0))
                .isEqualTo(PafiClassification.AT_RISK);
    }

    // ── MILD_ARDS branch ──────────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value exactly at 200 returns MILD_ARDS (lower boundary, inclusive)")
    void from_exactlyAt200_returnsMildArds() {
        assertThat(PafiClassification.from(200.0))
                .isEqualTo(PafiClassification.MILD_ARDS);
    }

    // ── MODERATE_ARDS branch ──────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value exactly at 100 returns MODERATE_ARDS (lower boundary, inclusive)")
    void from_exactlyAt100_returnsModerateArds() {
        assertThat(PafiClassification.from(100.0))
                .isEqualTo(PafiClassification.MODERATE_ARDS);
    }

    // ── SEVERE_ARDS branch ────────────────────────────────────────────────────

    @Test
    @DisplayName("from() — value strictly below 100 returns SEVERE_ARDS")
    void from_below100_returnsSevereArds() {
        assertThat(PafiClassification.from(99.99))
                .isEqualTo(PafiClassification.SEVERE_ARDS);
    }
}
