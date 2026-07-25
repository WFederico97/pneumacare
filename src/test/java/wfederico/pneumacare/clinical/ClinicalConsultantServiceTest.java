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
import wfederico.pneumacare.clinical.domain.DrivingPressureBand;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.CstatResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.PafiResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.RsbiResult;
import wfederico.pneumacare.clinical.infrastructure.persistence.ClinicalCombinationRuleJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.ClinicalCombinationRuleRepository;
import wfederico.pneumacare.clinical.infrastructure.persistence.MedicalReferenceJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.MedicalReferenceRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalConsultantServiceTest {

    @Mock
    private MedicalReferenceRepository referenceRepository;

    @Mock
    private ClinicalCombinationRuleRepository combinationRuleRepository;

    @InjectMocks
    private ClinicalConsultantService service;

    private MedicalReferenceJpaEntity ref(String metric, String band, String text, String source, int priority) {
        return MedicalReferenceJpaEntity.builder()
                .metric(metric).band(band)
                .rangeDescriptor("range").context("ctx")
                .guidanceText(text).sourceRef(source).priority(priority)
                .build();
    }

    private ClinicalCombinationRuleJpaEntity rule(String name, String rsbi, String pafi, String cstat,
                                                  String dp, String text, String source, int priority) {
        return ClinicalCombinationRuleJpaEntity.builder()
                .ruleName(name)
                .rsbiBand(rsbi).pafiBand(pafi).cstatBand(cstat).dpBand(dp)
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

        assertThat(guidance.text()).startsWith("Diferir la SBT");
        assertThat(guidance.text()).contains("• RSBI above 105 predicts weaning failure.");
        assertThat(guidance.text()).contains("Fuentes: Yang & Tobin 1991");
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

        assertThat(guidance.text()).isEqualTo("Sin datos de referencia suficientes para una recomendación.");
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

        // Verdict headline, then highest priority (PaFi 100) first, then RSBI 70, then Cstat 50.
        assertThat(guidance.text()).startsWith("Diferir la SBT\n\n• PaFi sentence.\n• RSBI sentence.\n• Cstat sentence.");
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
        assertThat(guidance.text()).endsWith("Fuentes: Berlin 2012");
    }

    @Test
    @DisplayName("a matching cross-metric rule composes before the single-metric guidance")
    void combinationRuleComposesFirst() {
        when(combinationRuleRepository.findAll()).thenReturn(List.of(
                rule("oxygenation-gates-weaning", "FAVORABLE", "MODERATE_ARDS,SEVERE_ARDS", null, null,
                        "Oxygenation gates weaning.", "Berlin 2012; Yang & Tobin 1991", 200)));
        when(referenceRepository.findByMetricAndBand("RSBI", "FAVORABLE")).thenReturn(Optional.empty());
        when(referenceRepository.findByMetricAndBand("PAFI", "MODERATE_ARDS"))
                .thenReturn(Optional.of(ref("PAFI", "MODERATE_ARDS", "Moderate ARDS guidance.", "Berlin 2012", 80)));
        when(referenceRepository.findByMetricAndBand("CSTAT", "NORMAL")).thenReturn(Optional.empty());

        ConsultantGuidance guidance = service.compose(
                result(RsbiInterpretation.FAVORABLE, PafiClassification.MODERATE_ARDS, CstatInterpretation.NORMAL));

        assertThat(guidance.text()).startsWith("Diferir la SBT\n\n• Oxygenation gates weaning.\n• Moderate ARDS guidance.");
        // Bundled citation is split and de-duplicated across both layers, order preserved.
        assertThat(guidance.sources()).containsExactly("Berlin 2012", "Yang & Tobin 1991");
    }

    @Test
    @DisplayName("a driving-pressure rule fires only when the HIGH band is supplied")
    void drivingPressureRuleFiresOnHighBand() {
        when(combinationRuleRepository.findAll()).thenReturn(List.of(
                rule("high-driving-pressure", null, null, null, "HIGH",
                        "Driving pressure exceeds 15 cmH2O.", "Amato 2015", 205)));
        when(referenceRepository.findByMetricAndBand("RSBI", "FAVORABLE")).thenReturn(Optional.empty());
        when(referenceRepository.findByMetricAndBand("PAFI", "NORMAL")).thenReturn(Optional.empty());
        when(referenceRepository.findByMetricAndBand("CSTAT", "NORMAL")).thenReturn(Optional.empty());

        ConsultantGuidance guidance = service.compose(
                result(RsbiInterpretation.FAVORABLE, PafiClassification.NORMAL, CstatInterpretation.NORMAL),
                DrivingPressureBand.HIGH);

        assertThat(guidance.text()).startsWith("SBT bajo monitoreo estrecho");
        assertThat(guidance.text()).contains("• Driving pressure exceeds 15 cmH2O.");
        assertThat(guidance.text()).endsWith("Fuentes: Amato 2015");
    }

    @Test
    @DisplayName("a driving-pressure rule does not fire when no driving-pressure band is available")
    void drivingPressureRuleSkippedWhenBandMissing() {
        when(combinationRuleRepository.findAll()).thenReturn(List.of(
                rule("high-driving-pressure", null, null, null, "HIGH",
                        "Driving pressure exceeds 15 cmH2O.", "Amato 2015", 205)));
        when(referenceRepository.findByMetricAndBand("RSBI", "FAVORABLE")).thenReturn(Optional.empty());
        when(referenceRepository.findByMetricAndBand("PAFI", "NORMAL")).thenReturn(Optional.empty());
        when(referenceRepository.findByMetricAndBand("CSTAT", "NORMAL")).thenReturn(Optional.empty());

        ConsultantGuidance guidance = service.compose(
                result(RsbiInterpretation.FAVORABLE, PafiClassification.NORMAL, CstatInterpretation.NORMAL));

        assertThat(guidance.text()).isEqualTo("Sin datos de referencia suficientes para una recomendación.");
    }

    @Test
    @DisplayName("a comma-separated band allow-list matches any listed band")
    void commaSeparatedBandAllowListMatches() {
        when(combinationRuleRepository.findAll()).thenReturn(List.of(
                rule("borderline-rsbi-acceptable-oxygenation", "BORDERLINE", "NORMAL,AT_RISK,MILD_ARDS", null, null,
                        "Monitored SBT is reasonable.", "AARC 2024", 120)));
        when(referenceRepository.findByMetricAndBand("RSBI", "BORDERLINE")).thenReturn(Optional.empty());
        when(referenceRepository.findByMetricAndBand("PAFI", "AT_RISK")).thenReturn(Optional.empty());
        when(referenceRepository.findByMetricAndBand("CSTAT", "NORMAL")).thenReturn(Optional.empty());

        ConsultantGuidance guidance = service.compose(
                result(RsbiInterpretation.BORDERLINE, PafiClassification.AT_RISK, CstatInterpretation.NORMAL));

        assertThat(guidance.text()).startsWith("SBT bajo monitoreo estrecho\n\n• Monitored SBT is reasonable.");
        assertThat(guidance.sources()).containsExactly("AARC 2024");
    }
}
