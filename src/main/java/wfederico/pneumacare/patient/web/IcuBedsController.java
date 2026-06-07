package wfederico.pneumacare.patient.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.patient.application.IcuBedService;
import wfederico.pneumacare.patient.web.dto.CreateIcuBedRequest;
import wfederico.pneumacare.patient.web.dto.IcuBedResponse;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import java.util.List;

/**
 * REST controller for ICU bed dashboard retrieval.
 *
 * <p>In staging/prod, access is OAuth2-protected and results are scoped by the
 * authenticated user's {@code icu_id} JWT claim. In dev, OAuth2 resource-server
 * auto-configuration is disabled and a deterministic ICU fallback is used.
 */
@Tag(name = "Beds", description = "ICU Beds management")
@Slf4j
@RestController
@RequestMapping("/api/v1/icu-beds")
@RequiredArgsConstructor
public class IcuBedsController {
    private final IcuBedService service;

    @Operation(
            summary = "Get ICU dashboard beds",
            description = """
                    Retrieves beds for the ICU associated with the authenticated user token.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Icu Beds retrieved successfully.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "status": 200,
                                              "message": "ICU Beds retrieved successfully",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "data": [
                                                {
                                                    "bedNumber": "BED-001",
                                                    "status": "AVAILABLE",
                                                },
                                                {
                                                    "bedNumber": "BED-002",
                                                    "status": "OCCUPIED",
                                                },
                                              ]
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required. Provide a valid Bearer token with " +
                            "scope `SCOPE_read` (staging/prod only).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid JWT claims (missing/invalid icu_id).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @GetMapping
    public ResponseEntity<ApiResponseBase<List<IcuBedResponse>>> getIcuBeds() {
        List<IcuBedResponse> data = service.findBedsForAuthenticatedIcu();
        return ResponseEntity.ok(
                ApiResponseBase.<List<IcuBedResponse>>builder()
                        .status(HttpStatus.OK.value())
                        .message(RequestMessageConstants.ICU_BEDS_RETRIEVED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponseBase<IcuBedResponse>> createIcuBed(
            @Valid @RequestBody CreateIcuBedRequest request) {
        IcuBedResponse data = service.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseBase.<IcuBedResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message(RequestMessageConstants.ICU_BED_CREATED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }
}
