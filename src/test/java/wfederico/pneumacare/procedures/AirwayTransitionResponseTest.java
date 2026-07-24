package wfederico.pneumacare.procedures;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.procedures.domain.AirwayEventType;
import wfederico.pneumacare.procedures.web.dto.AirwayTransitionResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The published transition table must mirror {@link AirwayEventType} exactly —
 * it exists so clients stop keeping their own copy of the state machine.
 */
class AirwayTransitionResponseTest {

    @Test
    @DisplayName("publishes every enum constant, so a new event type cannot be omitted")
    void publishesEveryEventType() {
        List<AirwayTransitionResponse> all = AirwayTransitionResponse.all();

        assertThat(all).hasSize(AirwayEventType.values().length);
        assertThat(all).extracting(AirwayTransitionResponse::eventType)
                .containsExactlyInAnyOrder(AirwayEventType.values());
    }

    @Test
    @DisplayName("each row carries the enum's own required and resulting statuses")
    void rowsMirrorTheEnumTransitions() {
        for (AirwayTransitionResponse row : AirwayTransitionResponse.all()) {
            assertThat(row.requiredCurrentStatus()).isEqualTo(row.eventType().requiredCurrentStatus());
            assertThat(row.resultingStatus()).isEqualTo(row.eventType().resultingStatus());
            assertThat(row.label()).isNotBlank();
        }
    }

    @Test
    @DisplayName("tracheostomy is no longer a dead end: decannulation leads out of it")
    void tracheostomyHasAnExit() {
        assertThat(AirwayTransitionResponse.all())
                .anySatisfy(row -> {
                    assertThat(row.requiredCurrentStatus()).isEqualTo(RespiratoryStatus.TRACHEOSTOMY);
                    assertThat(row.resultingStatus()).isEqualTo(RespiratoryStatus.SPONTANEOUS);
                });
    }
}
