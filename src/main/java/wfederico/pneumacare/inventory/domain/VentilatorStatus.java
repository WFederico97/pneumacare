package wfederico.pneumacare.inventory.domain;

/**
 * Hardware lifecycle states of a physical ventilator, matching the
 * {@code ck_physical_ventilators_status} DB constraint (V17).
 */
public enum VentilatorStatus {
    AVAILABLE,
    IN_USE,
    MAINTENANCE
}
