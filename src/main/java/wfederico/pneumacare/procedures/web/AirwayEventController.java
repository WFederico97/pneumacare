package wfederico.pneumacare.procedures.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.procedures.application.AirwayEventService;
import wfederico.pneumacare.procedures.web.dto.AirwayEventResponse;
import wfederico.pneumacare.procedures.web.dto.AirwayTransitionResponse;
import wfederico.pneumacare.procedures.web.dto.CreateAirwayEventRequest;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for airway events.
 *
 * <p>Authorization ({@code ROLE_THERAPIST} / {@code ROLE_CHIEF_OF_GUARD}) is
 * intentionally not enforced yet: authentication/login is a separate backlog
 * effort (next sprint). In dev all {@code /api/**} endpoints are open, consistent
 * with the other controllers.
 */
@Tag(name = "Airway Events",
        description = "Airway event registration and history (intubation / extubation / tracheostomy)")
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AirwayEventController {

    private final AirwayEventService service;

    @Operation(
            summary = "Register an airway event",
            description = "Records an intubation/extubation/tracheostomy and atomically advances "
                    + "the patient's respiratory status. Requires an OPEN shift for the patient's ICU.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Event registered; patient status advanced."),
            @ApiResponse(responseCode = "400", description = "Missing or malformed body."),
            @ApiResponse(responseCode = "404", description = "The referenced patient does not exist."),
            @ApiResponse(responseCode = "409", description = "No OPEN shift, or illegal airway transition.")
    })
    @PreAuthorize("hasRole('THERAPIST')")
    @PostMapping("/procedures/airway")
    public ResponseEntity<ApiResponseBase<AirwayEventResponse>> registerAirwayEvent(
            @Valid @RequestBody CreateAirwayEventRequest request) {
        AirwayEventResponse data = service.register(request);

        URI location = URI.create("/api/v1/patients/" + data.patientId() + "/airway-events");
        return ResponseEntity
                .created(location)
                .body(ApiResponseBase.<AirwayEventResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message(RequestMessageConstants.AIRWAY_EVENT_REGISTERED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    @Operation(
            summary = "List the legal airway transitions",
            description = "Publishes the airway state machine (event type, required current status, "
                    + "resulting status, display label) so clients render transitions from the server "
                    + "instead of hardcoding their own copy.")
    @ApiResponse(responseCode = "200", description = "The complete transition table.")
    @PreAuthorize("hasRole('THERAPIST')")
    @GetMapping("/procedures/airway/transitions")
    public ResponseEntity<ApiResponseBase<List<AirwayTransitionResponse>>> airwayTransitions() {
        return ResponseEntity.ok(ApiResponseBase.<List<AirwayTransitionResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Transiciones de vía aérea recuperadas")
                .data(AirwayTransitionResponse.all())
                .traceId(MDC.get("traceId"))
                .build());
    }

    @Operation(
            summary = "List a patient's airway events",
            description = "Returns the patient's airway events ordered by event time, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events returned (possibly empty)."),
            @ApiResponse(responseCode = "404", description = "The referenced patient does not exist.")
    })
    @PreAuthorize("hasAnyRole('THERAPIST','COMPLIANCE')")
    @GetMapping("/patients/{patientId}/airway-events")
    public ResponseEntity<ApiResponseBase<List<AirwayEventResponse>>> getPatientAirwayEvents(
            @PathVariable UUID patientId) {
        List<AirwayEventResponse> data = service.getPatientAirwayEvents(patientId);

        return ResponseEntity.ok(
                ApiResponseBase.<List<AirwayEventResponse>>builder()
                        .status(HttpStatus.OK.value())
                        .message(RequestMessageConstants.AIRWAY_EVENTS_RETRIEVED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }
}
