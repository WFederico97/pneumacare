package wfederico.pneumacare.config.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.config.application.SystemSettingService;
import wfederico.pneumacare.config.web.dto.SystemSettingResponse;
import wfederico.pneumacare.config.web.dto.UpdateSettingRequest;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import java.util.List;

/**
 * Admin-only centralized configuration hub. Exposes the system-wide settings
 * catalog and lets administrators update editable values from a single place.
 */
@Tag(name = "Admin settings", description = "Centralized system configuration")
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingService service;

    @Operation(summary = "List all system settings grouped by category")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponseBase<List<SystemSettingResponse>>> list() {
        return ResponseEntity.ok(ApiResponseBase.<List<SystemSettingResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Configuración del sistema")
                .data(service.listAll())
                .traceId(MDC.get("traceId"))
                .build());
    }

    @Operation(summary = "Update one editable system setting")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{settingKey}")
    public ResponseEntity<ApiResponseBase<SystemSettingResponse>> update(
            @PathVariable String settingKey,
            @Valid @RequestBody UpdateSettingRequest request) {
        return ResponseEntity.ok(ApiResponseBase.<SystemSettingResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Configuración actualizada")
                .data(service.update(settingKey, request.value()))
                .traceId(MDC.get("traceId"))
                .build());
    }
}
