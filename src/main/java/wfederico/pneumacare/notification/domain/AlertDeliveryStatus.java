package wfederico.pneumacare.notification.domain;

/** Lifecycle of a dispatched patient-risk alert in {@code clinical_alerts_log}. */
public enum AlertDeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED
}
