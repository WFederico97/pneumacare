package wfederico.pneumacare.inventory.domain;

/**
 * Ventilator brands accepted for inventory registration.
 *
 * <p>Intentionally separate from {@code clinical.domain.VentilatorBrand}:
 * bounded contexts do not share domain types. The clinical enum drives
 * evaluation math strategies; this one is the inventory catalogue value
 * persisted (as a string) in {@code ventilator_models.brand}.
 */
public enum VentilatorBrand {
    TECME,
    NEUMOVENT
}
