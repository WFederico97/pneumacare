package wfederico.pneumacare.notification.application;

/** Outbound port for sending a patient-risk alert to the external channel. */
public interface WebhookNotificationPort {

    /**
     * Sends the alert. Implementations throw a {@link RuntimeException}
     * (e.g. {@code RestClientException}) on transport failure; the caller
     * decides how to handle it (PNMC-99: log, no retry).
     */
    void send(AlertNotification notification);
}
