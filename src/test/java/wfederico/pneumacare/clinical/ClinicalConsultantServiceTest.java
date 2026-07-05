package wfederico.pneumacare.clinical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wfederico.pneumacare.clinical.application.ClinicalConsultantService;
import wfederico.pneumacare.clinical.domain.ConsultantGuidance;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.CstatResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.PafiResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.RsbiResult;
import wfederico.pneumacare.clinical.infrastructure.persistence.MedicalReferenceJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.MedicalReferenceRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalConsultantServiceTest {

    @Mock
    private MedicalReferenceRepository referenceRepository;

    @InjectMocks
    private ClinicalConsultantService service;

    private MedicalReferenceJpaEntity ref(String metric, String band, String text, String source, int priority) {
        return MedicalReferenceJpaEntity.builder()
                .metric(metric).band(band)
                .rangeDescriptor("range").context("ctx")
                .guidanceText(text).sourceRef(source).priority(priority)
                .build();
    }

    private VentilatorEvaluationResult result(RsbiInterpretation rsbi, PafiClassification pafi, CstatInterpretation cstat) {
        return new VentilatorEvaluationResult(
                new RsbiResult(110.0, rsbi),
                new PafiResult(180.0, pafi),
                new CstatResult(40.0, cstat));
    }

    @Test
    @DisplayName("RSBI breach alone composes from the matching entry with no other interaction")
    void rsbiBreachComposesFromMatch() {
        when(referenceRepository.findByMetricAndBand("RSBI", "UNFAVORABLE"))
                .thenReturn(Optional.of(ref("RSBI", "UNFAVORABLE",
                        "RSBI above 105 predicts weaning failure.", "Yang & Tobin 1991", 70)));
        when(referenceRepository.findByMetricAndBand("PAFI", "NORMAL")).thenReturn(Optional.empty());
        when(referenceRepository.findByMetricAndBand("CSTAT", "NORMAL")).thenReturn(Optional.empty());

        ConsultantGuidance guidance = service.compose(
                result(RsbiInterpretation.UNFAVORABLE, PafiClassification.NORMAL, CstatInterpretation.NORMAL));

        assertThat(guidance.text()).contains("RSBI above 105 predicts weaning failure.");
        assertThat(guidance.text()).contains("Ref: Yang & Tobin 1991");
        assertThat(guidance.sources()).containsExactly("Yang & Tobin 1991");
        verifyNoMoreInteractions(referenceRepository);
    }

    @Test
    @DisplayName("all-normal metrics return the safe default with empty sources")
    void allNormalReturnsSafeDefault() {
        when(referenceRepository.findByMetricAndBand("RSBI", "FAVORABLE")).thenReturn(Optional.empty());
        when(referenceRepository.findByMetricAndBand("PAFI", "NORMAL")).thenReturn(Optional.empty());
        when(referenceRepository.findByMetricAndBand("CSTAT", "NORMAL")).thenReturn(Optional.empty());

        ConsultantGuidance guidance = service.compose(
                result(RsbiInterpretation.FAVORABLE, PafiClassification.NORMAL, CstatInterpretation.NORMAL));

        assertThat(guidance.text()).isEqualTo("insufficient reference data");
        assertThat(guidance.sources()).isEmpty();
    }

    @Test
    @DisplayName("multiple abnormal metrics compose in descending priority order, capped at 3")
    void multipleAbnormalComposeByPriority() {
        when(referenceRepository.findByMetricAndBand("RSBI", "UNFAVORABLE"))
                .thenReturn(Optional.of(ref("RSBI", "UNFAVORABLE", "RSBI sentence.", "SrcR", 70)));
        when(referenceRepository.findByMetricAndBand("PAFI", "SEVERE_ARDS"))
                .thenReturn(Optional.of(ref("PAFI", "SEVERE_ARDS", "PaFi sentence.", "SrcP", 100)));
        when(referenceRepository.findByMetricAndBand("CSTAT", "LOW"))
                .thenReturn(Optional.of(ref("CSTAT", "LOW", "Cstat sentence.", "SrcC", 50)));

        ConsultantGuidance guidance = service.compose(
                result(RsbiInterpretation.UNFAVORABLE, PafiClassification.SEVERE_ARDS, CstatInterpretation.LOW));

        // Highest priority (PaFi 100) first, then RSBI 70, then Cstat 50.
        assertThat(guidance.text()).startsWith("PaFi sentence. RSBI sentence. Cstat sentence.");
        assertThat(guidance.text().indexOf("PaFi sentence."))
                .isLessThan(guidance.text().indexOf("RSBI sentence."));
        assertThat(guidance.text().indexOf("RSBI sentence."))
                .isLessThan(guidance.text().indexOf("Cstat sentence."));
        assertThat(guidance.sources()).containsExactly("SrcP", "SrcR", "SrcC");
    }

    @Test
    @DisplayName("duplicate source references are de-duplicated in sources and citation")
    void duplicateSourcesAreDeduped() {
        when(referenceRepository.findByMetricAndBand("PAFI", "MODERATE_ARDS"))
                .thenReturn(Optional.of(ref("PAFI", "MODERATE_ARDS", "Moderate.", "Berlin 2012", 80)));
        when(referenceRepository.findByMetricAndBand("RSBI", "FAVORABLE")).thenReturn(Optional.empty());
        // Reuse the PaFi source on the Cstat entry to force a duplicate.
        when(referenceRepository.findByMetricAndBand("CSTAT", "LOW"))
                .thenReturn(Optional.of(ref("CSTAT", "LOW", "Low compliance.", "Berlin 2012", 50)));

        ConsultantGuidance guidance = service.compose(
                result(RsbiInterpretation.FAVORABLE, PafiClassification.MODERATE_ARDS, CstatInterpretation.LOW));

        assertThat(guidance.sources()).containsExactly("Berlin 2012");
        assertThat(guidance.text()).endsWith("Ref: Berlin 2012");
    }
}
