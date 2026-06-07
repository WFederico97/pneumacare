package wfederico.pneumacare.shared.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiMaskingUtil")
class PiiMaskingUtilTest {

    // ── maskDni ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("maskDni_8digits_showsFirstTwoAndLastTwo")
    void maskDni_8digits_showsFirstTwoAndLastTwo() {
        assertThat(PiiMaskingUtil.maskDni("35123456")).isEqualTo("35****56");
    }

    @Test
    @DisplayName("maskDni_7digits_showsFirstTwoAndLastTwo")
    void maskDni_7digits_showsFirstTwoAndLastTwo() {
        assertThat(PiiMaskingUtil.maskDni("3512345")).isEqualTo("35***45");
    }

    @Test
    @DisplayName("maskDni_4orFewerDigits_fullyMasked")
    void maskDni_4orFewerDigits_fullyMasked() {
        assertThat(PiiMaskingUtil.maskDni("1234")).isEqualTo("****");
        assertThat(PiiMaskingUtil.maskDni("12")).isEqualTo("**");
    }

    @Test
    @DisplayName("maskDni_null_returnsNullToken")
    void maskDni_null_returnsNullToken() {
        assertThat(PiiMaskingUtil.maskDni(null)).isEqualTo("[NULL]");
    }

    @Test
    @DisplayName("maskDni_result_doesNotContainOriginalMiddleDigits")
    void maskDni_result_doesNotContainOriginalMiddleDigits() {
        String masked = PiiMaskingUtil.maskDni("35123456");
        assertThat(masked).doesNotContain("1234");
    }

    // ── maskName ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("maskName_multiCharName_showsOnlyFirstChar")
    void maskName_multiCharName_showsOnlyFirstChar() {
        assertThat(PiiMaskingUtil.maskName("Juan")).isEqualTo("J***");
        assertThat(PiiMaskingUtil.maskName("María")).isEqualTo("M****");
    }

    @Test
    @DisplayName("maskName_singleChar_returnsUnchanged")
    void maskName_singleChar_returnsUnchanged() {
        assertThat(PiiMaskingUtil.maskName("A")).isEqualTo("A");
    }

    @Test
    @DisplayName("maskName_blank_returnsBlankToken")
    void maskName_blank_returnsBlankToken() {
        assertThat(PiiMaskingUtil.maskName("   ")).isEqualTo("[BLANK]");
        assertThat(PiiMaskingUtil.maskName("")).isEqualTo("[BLANK]");
    }

    @Test
    @DisplayName("maskName_null_returnsNullToken")
    void maskName_null_returnsNullToken() {
        assertThat(PiiMaskingUtil.maskName(null)).isEqualTo("[NULL]");
    }

    @Test
    @DisplayName("maskName_result_doesNotContainOriginalName")
    void maskName_result_doesNotContainOriginalName() {
        String masked = PiiMaskingUtil.maskName("Juan");
        assertThat(masked).doesNotContain("uan");
    }

    // ── redact ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("redact_anyValue_returnsRedactedToken")
    void redact_anyValue_returnsRedactedToken() {
        assertThat(PiiMaskingUtil.redact("27-35123456-4")).isEqualTo("[REDACTED]");
        assertThat(PiiMaskingUtil.redact("sensitive")).isEqualTo("[REDACTED]");
    }

    @Test
    @DisplayName("redact_null_returnsNullToken")
    void redact_null_returnsNullToken() {
        assertThat(PiiMaskingUtil.redact(null)).isEqualTo("[NULL]");
    }
}
