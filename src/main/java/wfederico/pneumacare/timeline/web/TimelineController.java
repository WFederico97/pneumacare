package wfederico.pneumacare.timeline.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;
import wfederico.pneumacare.timeline.application.TimelineService;
import wfederico.pneumacare.timeline.web.dto.TimelineEntryResponse;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the unified patient clinical timeline (PNMC-133).
 *
 * <p>Server-side aggregator that merges evaluations, airway events and SBTs into one
 * feed ordered newest-first, so the patient detail view (PNMC-96) renders a single
 * timeline without merging multiple calls client-side.
 *
 * <h2>Security note</h2>
 * Role enforcement ({@code ROLE_THERAPIST} / {@code ROLE_CHIEF_OF_GUARD}) is
 * intentionally not applied yet: authentication/login is a separate backlog effort.
 * In dev all {@code /api/**} endpoints are open, consistent with the other
 * controllers; the {@code 401}/{@code 403} behaviour lands with the auth story.
 */
@Tag(name = "Patient Timeline",
        description = "Unified, chronologically-ordered feed of a patient's clinical events")
@Slf4j
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService service;

    @Operation(
            summary = "Get a patient's unified clinical timeline",
            description = "Returns one list merging the patient's ventilator evaluations, airway "
                    + "events and SBTs, ordered by occurrence time (newest first). Returns an empty "
                    + "list for a patient with no recorded events.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timeline returned (possibly empty)."),
            @ApiResponse(responseCode = "404", description = "The referenced patient does not exist.")
    })
    @PreAuthorize("hasAnyRole('THERAPIST','COMPLIANCE')")
    @GetMapping("/{id}/timeline")
    public ResponseEntity<ApiResponseBase<List<TimelineEntryResponse>>> getTimeline(
            @Parameter(description = "Operational patient UUID (patients.id).",
                    example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            @PathVariable UUID id) {

        List<TimelineEntryResponse> data = service.getTimeline(id);

        return ResponseEntity.ok(
                ApiResponseBase.<List<TimelineEntryResponse>>builder()
                        .status(HttpStatus.OK.value())
                        .message(RequestMessageConstants.TIMELINE_RETRIEVED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }
}
