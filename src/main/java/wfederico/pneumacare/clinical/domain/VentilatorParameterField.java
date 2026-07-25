package wfederico.pneumacare.clinical.domain;

/**
 * Describes one brand-specific extended ventilator parameter the UI must render
 * dynamically. This is the config-driven contract that lets the evaluation form
 * adapt its input fields to the selected equipment without hard-coding them.
 *
 * @param key       machine key stored in the evaluation's {@code extendedParameters} JSON
 * @param label     human label shown next to the input (Spanish)
 * @param unit      unit suffix shown in the input (e.g. {@code "L/min"}), may be blank
 * @param valueType UI hint: currently always {@code "number"}
 * @param min       inclusive minimum accepted value
 * @param max       inclusive maximum accepted value
 * @param step      input step increment
 * @param required  whether the field must be filled to submit
 */
public record VentilatorParameterField(
        String key,
        String label,
        String unit,
        String valueType,
        double min,
        double max,
        double step,
        boolean required) {
}
