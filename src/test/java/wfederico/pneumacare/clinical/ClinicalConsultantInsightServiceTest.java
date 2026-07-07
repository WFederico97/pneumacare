package wfederico.pneumacare.clinical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import wfederico.pneumacare.clinical.application.ClinicalConsultantInsightService;
import wfederico.pneumacare.clinical.application.ClinicalConsultantService;
import wfederico.pneumacare.clinical.domain.ConsultantGuidance;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.infrastructure.persistence.ClinicalConsultantInsightJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.ClinicalConsultantInsightRepository;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.clinical.web.dto.InsightResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalConsultantInsightServiceTest {

    private static final UUID EVAL_ID = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final String COMPOSED = "RSBI above 105 predicts weaning failure. Ref: Yang & Tobin";

    @Mock
    private ClinicalConsultantInsightRepository insightRepository;
    @Mock
    private EvaluationRepository evaluationRepository;
    @Mock
    private ClinicalConsultantService consultantService;

    @InjectMocks
    private ClinicalConsultantInsightService service;

    private EvaluationJpaEntity evaluation() {
        return EvaluationJpaEntity.builder()
                .id(EVAL_ID)
                .rsbiSnapshot(new BigDecimal("110.00"))
                .pafiSnapshot(new BigDecimal("420.00"))
                .cstatSnapshot(new BigDecimal("80.00"))
                .rsbiInterpretation(RsbiInterpretation.UNFAVORABLE)
                .pafiClassification(PafiClassification.NORMAL)
                .cstatInterpretation(CstatInterpretation.NORMAL)
                .build();
    }

    private ClinicalConsultantInsightJpaEntity stored(String text) {
        return ClinicalConsultantInsightJpaEntity.builder()
                .id(UUID.randomUUID())
                .evaluationId(EVAL_ID)
                .insightText(text)
                .build();
    }

    @Test
    @DisplayName("cache miss composes guidance, persists it, and returns cached=false")
    void cacheMissComposesAndPersists() {
        when(insightRepository.findByEvaluationId(EVAL_ID)).thenReturn(Optional.empty());
        when(evaluationRepository.findById(EVAL_ID)).thenReturn(Optional.of(evaluation()));
        when(consultantService.compose(any(VentilatorEvaluationResult.class)))
                .thenReturn(new ConsultantGuidance(COMPOSED, List.of("Yang & Tobin")));
        when(insightRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        InsightResponse response = service.getOrCreate(EVAL_ID);

        assertThat(response.evaluationId()).isEqualTo(EVAL_ID);
        assertThat(response.insightText()).isEqualTo(COMPOSED);
        assertThat(response.cached()).isFalse();
        verify(insightRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("cache hit returns stored text without recomputing")
    void cacheHitReturnsStored() {
        when(insightRepository.findByEvaluationId(EVAL_ID)).thenReturn(Optional.of(stored(COMPOSED)));

        InsightResponse response = service.getOrCreate(EVAL_ID);

        assertThat(response.insightText()).isEqualTo(COMPOSED);
        assertThat(response.cached()).isTrue();
        verify(consultantService, never()).compose(any());
        verify(evaluationRepository, never()).findById(any());
    }

    @Test
    @DisplayName("unknown evaluation id throws 404")
    void unknownEvaluationThrows404() {
        when(insightRepository.findByEvaluationId(EVAL_ID)).thenReturn(Optional.empty());
        when(evaluationRepository.findById(EVAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreate(EVAL_ID))
                .isInstanceOf(BusinessLayerException.class)
                .hasMessageContaining(EVAL_ID.toString());
        verify(consultantService, never()).compose(any());
    }

    @Test
    @DisplayName("concurrent miss re-reads the winner on unique-constraint violation")
    void concurrentMissReReadsWinner() {
        when(insightRepository.findByEvaluationId(EVAL_ID))
                .thenReturn(Optional.empty(), Optional.of(stored(COMPOSED)));
        when(evaluationRepository.findById(EVAL_ID)).thenReturn(Optional.of(evaluation()));
        when(consultantService.compose(any(VentilatorEvaluationResult.class)))
                .thenReturn(new ConsultantGuidance(COMPOSED, List.of("Yang & Tobin")));
        when(insightRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate evaluation_id"));

        InsightResponse response = service.getOrCreate(EVAL_ID);

        assertThat(response.insightText()).isEqualTo(COMPOSED);
        assertThat(response.cached()).isTrue();
    }
}
