package wfederico.pneumacare.timeline.domain;

/**
 * Discriminator for a patient-timeline entry's source (PNMC-133).
 *
 * <p>Each value identifies which bounded context produced the event and, by
 * extension, the concrete shape of the entry's {@code payload}.
 */
public enum TimelineEventType {
    /** A ventilator evaluation from the {@code clinical} context. */
    EVALUATION,
    /** An airway event (intubation / extubation / tracheostomy) from {@code procedures}. */
    AIRWAY,
    /** A spontaneous breathing trial from {@code procedures}. */
    SBT
}
