package wfederico.backendjavacoretemplate.infra.adapter.in.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import wfederico.backendjavacoretemplate.application.port.in.PlayerUseCase;
import wfederico.backendjavacoretemplate.core.constants.RequestMessageConstants;
import wfederico.backendjavacoretemplate.core.web.ApiResponseBase;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerPatchDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerRequestDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerResponseDTO;

@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
@Tag(name = "Players", description = "Player CRUD operations")
public class PlayerController {

    private final PlayerUseCase playerService;

    @GetMapping
    @Operation(summary = "List all players (paginated)")
    @ApiResponse(responseCode = "200", description = "Players retrieved")
    public ResponseEntity<ApiResponseBase<Page<PlayerResponseDTO>>> getAll(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")             @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field")            @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "asc or desc")          @RequestParam(defaultValue = "asc") String direction) {

        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(ApiResponseBase.<Page<PlayerResponseDTO>>builder()
                .status(HttpStatus.OK.value())
                .message(RequestMessageConstants.PLAYERS_FOUND)
                .data(playerService.getAllPlayersPaged(pageable))
                .traceId(MDC.get("traceId"))
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get player by ID")
    @ApiResponse(responseCode = "200", description = "Player found")
    @ApiResponse(responseCode = "404", description = "Player not found")
    public ResponseEntity<ApiResponseBase<PlayerResponseDTO>> getPlayer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseBase.<PlayerResponseDTO>builder()
                .status(HttpStatus.OK.value())
                .message(RequestMessageConstants.PLAYER_FOUND)
                .data(playerService.getPlayerById(id))
                .traceId(MDC.get("traceId"))
                .build());
    }

    @PostMapping
    @Operation(summary = "Create a new player")
    @ApiResponse(responseCode = "201", description = "Player created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    public ResponseEntity<ApiResponseBase<PlayerResponseDTO>> createPlayer(
            @Valid @RequestBody PlayerRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseBase.<PlayerResponseDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message(RequestMessageConstants.PLAYER_CREATED)
                .data(playerService.createPlayer(request))
                .traceId(MDC.get("traceId"))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Full update of an existing player")
    @ApiResponse(responseCode = "200", description = "Player updated")
    @ApiResponse(responseCode = "404", description = "Player not found")
    public ResponseEntity<ApiResponseBase<PlayerResponseDTO>> updatePlayer(
            @PathVariable Long id, @Valid @RequestBody PlayerRequestDTO request) {
        return ResponseEntity.ok(ApiResponseBase.<PlayerResponseDTO>builder()
                .status(HttpStatus.OK.value())
                .message(RequestMessageConstants.PLAYER_UPDATED)
                .data(playerService.updatePlayer(id, request))
                .traceId(MDC.get("traceId"))
                .build());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partial update of a player")
    @ApiResponse(responseCode = "200", description = "Player partially updated")
    @ApiResponse(responseCode = "404", description = "Player not found")
    public ResponseEntity<ApiResponseBase<PlayerResponseDTO>> patchPlayer(
            @PathVariable Long id, @Valid @RequestBody PlayerPatchDTO request) {
        return ResponseEntity.ok(ApiResponseBase.<PlayerResponseDTO>builder()
                .status(HttpStatus.OK.value())
                .message(RequestMessageConstants.PLAYER_PATCHED)
                .data(playerService.patchPlayer(id, request))
                .traceId(MDC.get("traceId"))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a player by ID")
    @ApiResponse(responseCode = "200", description = "Player deleted")
    @ApiResponse(responseCode = "404", description = "Player not found")
    public ResponseEntity<ApiResponseBase<Void>> deletePlayer(@PathVariable Long id) {
        playerService.deletePlayer(id);
        return ResponseEntity.ok(ApiResponseBase.<Void>builder()
                .status(HttpStatus.OK.value())
                .message(RequestMessageConstants.PLAYER_DELETED)
                .traceId(MDC.get("traceId"))
                .build());
    }
}

