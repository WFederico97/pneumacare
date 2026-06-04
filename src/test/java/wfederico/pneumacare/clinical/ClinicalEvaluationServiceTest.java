package wfederico.pneumacare.clinical;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import wfederico.pneumacare.clinical.application.ClinicalEvaluationService;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.web.dto.CstatRequest;
import wfederico.pneumacare.clinical.web.dto.CstatResponse;
import wfederico.pneumacare.clinical.web.dto.PafiRequest;
import wfederico.pneumacare.clinical.web.dto.PafiResponse;
import wfederico.pneumacare.clinical.web.dto.RsbiRequest;
import wfederico.pneumacare.clinical.web.dto.RsbiResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClinicalEvaluationServiceTest {
    private ClinicalEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new ClinicalEvaluationService();
    }

    // ── RSBI interpretations ──────────────────────────────────────────────────

    @Test
    @DisplayName("RSBI — value below 80 is interpreted as FAVORABLE")
    void calculateRsbi_below80_returnsFavorable() {
        // 30 / 1.0 = 30 → FAVORABLE
        RsbiResponse result = service.calculateRsbi(new RsbiRequest(30.0, 1.0));

        assertThat(result.rsbi()).isEqualTo(30.0);
        assertThat(result.interpretation()).isEqualTo(RsbiInterpretation.FAVORABLE);
    }

    @Test
    @DisplayName("RSBI — value exactly at 80 is interpreted as BORDERLINE")
    void calculateRsbi_exactlyAt80_returnsBorderline() {
        // 80 / 1.0 = 80 → BORDERLINE
        RsbiResponse result = service.calculateRsbi(new RsbiRequest(80.0, 1.0));

        assertThat(result.interpretation()).isEqualTo(RsbiInterpretation.BORDERLINE);
    }

    @Test
    @DisplayName("RSBI — value exactly at 105 is interpreted as BORDERLINE")
    void calculateRsbi_exactlyAt105_returnsBorderline() {
        // 105 / 1.0 = 105 → BORDERLINE
        RsbiResponse result = service.calculateRsbi(new RsbiRequest(105.0, 1.0));

        assertThat(result.interpretation()).isEqualTo(RsbiInterpretation.BORDERLINE);
    }

    @Test
    @DisplayName("RSBI — value above 105 is interpreted as UNFAVORABLE")
    void calculateRsbi_above105_returnsUnfavorable() {
        // 30 / 0.25 = 120 → UNFAVORABLE
        RsbiResponse result = service.calculateRsbi(new RsbiRequest(30.0, 0.25));

        assertThat(result.interpretation()).isEqualTo(RsbiInterpretation.UNFAVORABLE);
    }

    // ── PaFi classifications ──────────────────────────────────────────────────

    @Test
    @DisplayName("PaFi — value >= 400 is classified as NORMAL")
    void calculatePafi_above400_returnsNormal() {
        PafiResponse result = service.calculatePafi(new PafiRequest(400.0, 1.0));

        assertThat(result.classification()).isEqualTo(PafiClassification.NORMAL);
    }

    @Test
    @DisplayName("PaFi — value exactly at 300 is classified as AT_RISK")
    void calculatePafi_exactlyAt300_returnsAtRisk() {
        PafiResponse result = service.calculatePafi(new PafiRequest(300.0, 1.0));

        assertThat(result.classification()).isEqualTo(PafiClassification.AT_RISK);
    }

    @Test
    @DisplayName("PaFi — value exactly at 200 is classified as MILD_ARDS")
    void calculatePafi_exactlyAt200_returnsMildArds() {
        PafiResponse result = service.calculatePafi(new PafiRequest(200.0, 1.0));

        assertThat(result.classification()).isEqualTo(PafiClassification.MILD_ARDS);
    }

    @Test
    @DisplayName("PaFi — value exactly at 100 is classified as MODERATE_ARDS")
    void calculatePafi_exactlyAt100_returnsModerateArds() {
        PafiResponse result = service.calculatePafi(new PafiRequest(100.0, 1.0));

        assertThat(result.classification()).isEqualTo(PafiClassification.MODERATE_ARDS);
    }

    @Test
    @DisplayName("PaFi — value below 100 is classified as SEVERE_ARDS")
    void calculatePafi_below100_returnsSevereArds() {
        PafiResponse result = service.calculatePafi(new PafiRequest(80.0, 1.0));

        assertThat(result.classification()).isEqualTo(PafiClassification.SEVERE_ARDS);
    }

    // ── Cstat interpretations ─────────────────────────────────────────────────

    @Test
    @DisplayName("Cstat — value >= 100 is interpreted as HIGH")
    void calculateCstat_above100_returnsHigh() {
        // 2000 / (25 - 5) = 100.0 → HIGH
        CstatResponse result = service.calculateCstat(new CstatRequest(2000.0, 25.0, 5.0));

        assertThat(result.cstat()).isEqualTo(100.0);
        assertThat(result.interpretation()).isEqualTo(CstatInterpretation.HIGH);
    }

    @Test
    @DisplayName("Cstat — value exactly at 50 is interpreted as NORMAL")
    void calculateCstat_exactlyAt50_returnsNormal() {
        // 1000 / (25 - 5) = 50.0 → NORMAL
        CstatResponse result = service.calculateCstat(new CstatRequest(1000.0, 25.0, 5.0));

        assertThat(result.cstat()).isEqualTo(50.0);
        assertThat(result.interpretation()).isEqualTo(CstatInterpretation.NORMAL);
    }

    @Test
    @DisplayName("Cstat — value below 50 is interpreted as LOW")
    void calculateCstat_below50_returnsLow() {
        // 500 / (25 - 5) = 25.0 → LOW
        CstatResponse result = service.calculateCstat(new CstatRequest(500.0, 25.0, 5.0));

        assertThat(result.cstat()).isEqualTo(25.0);
        assertThat(result.interpretation()).isEqualTo(CstatInterpretation.LOW);
    }

    @Test
    @DisplayName("Cstat — pplat equal to peepTotal propagates IllegalArgumentException")
    void calculateCstat_pplatEqualsPeep_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.calculateCstat(new CstatRequest(500.0, 10.0, 10.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
