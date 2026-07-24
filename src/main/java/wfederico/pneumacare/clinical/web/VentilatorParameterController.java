package wfederico.pneumacare.clinical.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.clinical.application.strategy.VentilatorFactory;
import wfederico.pneumacare.clinical.domain.VentilatorBrand;
import wfederico.pneumacare.clinical.web.dto.VentilatorParameterSchemaResponse;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import java.util.Arrays;
import java.util.List;

/**
 * Exposes the config-driven ventilator parameter schema so the evaluation form
 * can adapt its input fields to the selected brand/model at runtime. The schema
 * is sourced from each brand's {@link wfederico.pneumacare.clinical.application.strategy.VentilatorStrategy},
 * keeping the backend the single source of truth for which parameters each
 * piece of equipment requires.
 */
@Tag(name = "Ventilator parameters", description = "Dynamic ventilator parameterization schema")
@RestController
@RequestMapping("/api/v1/ventilator-parameters")
@RequiredArgsConstructor
public class VentilatorParameterController {

    private final VentilatorFactory ventilatorFactory;

    @Operation(summary = "List the extended parameter schema for every supported ventilator brand")
    @PreAuthorize("hasAnyRole('ADMIN','CHIEF_OF_GUARD','THERAPIST')")
    @GetMapping
    public ResponseEntity<ApiResponseBase<List<VentilatorParameterSchemaResponse>>> list() {
        List<VentilatorParameterSchemaResponse> schema = Arrays.stream(VentilatorBrand.values())
                .map(brand -> new VentilatorParameterSchemaResponse(
                        brand, ventilatorFactory.resolve(brand).extendedParameters()))
                .toList();
        return ResponseEntity.ok(ApiResponseBase.<List<VentilatorParameterSchemaResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Esquema de parámetros de ventilador")
                .data(schema)
                .traceId(MDC.get("traceId"))
                .build());
    }
}
