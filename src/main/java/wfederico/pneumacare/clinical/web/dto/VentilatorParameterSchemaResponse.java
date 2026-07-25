package wfederico.pneumacare.clinical.web.dto;

import wfederico.pneumacare.clinical.domain.VentilatorBrand;
import wfederico.pneumacare.clinical.domain.VentilatorParameterField;

import java.util.List;

/**
 * The dynamic parameter schema for one ventilator brand: the brand-specific
 * extended fields the evaluation form must render on top of the six universal
 * parameters. Lets the UI adapt its inputs to the selected equipment.
 */
public record VentilatorParameterSchemaResponse(
        VentilatorBrand brand,
        List<VentilatorParameterField> extendedFields) {
}
