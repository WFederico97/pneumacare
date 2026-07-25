package wfederico.pneumacare.procedures.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.procedures.domain.AirwayEventType;

import java.util.Arrays;
import java.util.List;

/**
 * One legal airway transition, published so clients render the state machine
 * instead of re-declaring it.
 *
 * <p>Clients previously hardcoded the allowed event types per respiratory
 * status; adding {@link AirwayEventType#DECANNULATION} to the server left those
 * copies stale and the transition unreachable. This endpoint makes
 * {@link AirwayEventType} the single source of truth.
 */
@Schema(description = "A legal airway transition: which event applies to which current status.")
public record AirwayTransitionResponse(

        @Schema(description = "Airway event type.", example = "DECANNULATION")
        AirwayEventType eventType,

        @Schema(description = "Status the patient must be in for this event to be legal.",
                example = "TRACHEOSTOMY")
        RespiratoryStatus requiredCurrentStatus,

        @Schema(description = "Status the patient moves to once the event is applied.",
                example = "SPONTANEOUS")
        RespiratoryStatus resultingStatus,

        @Schema(description = "Spanish clinical label for display.", example = "Decanulación")
        String label) {

    /** The complete state machine, derived from the enum. */
    public static List<AirwayTransitionResponse> all() {
        return Arrays.stream(AirwayEventType.values())
                .map(t -> new AirwayTransitionResponse(
                        t, t.requiredCurrentStatus(), t.resultingStatus(), labelOf(t)))
                .toList();
    }

    private static String labelOf(AirwayEventType type) {
        return switch (type) {
            case INTUBATION -> "Intubación";
            case EXTUBATION -> "Extubación";
            case TRACHEOSTOMY -> "Traqueostomía";
            case DECANNULATION -> "Decanulación";
        };
    }
}
