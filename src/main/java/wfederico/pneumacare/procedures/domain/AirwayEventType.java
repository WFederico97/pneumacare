package wfederico.pneumacare.procedures.domain;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;

/**
 * Airway event types and the airway state machine they drive.
 *
 * <p>Each event encodes its allowed transition as
 * {@code requiredCurrentStatus -> resultingStatus}:
 *
 * <ul>
 *   <li>{@link #INTUBATION}:   SPONTANEOUS &rarr; INTUBATED</li>
 *   <li>{@link #EXTUBATION}:   INTUBATED   &rarr; SPONTANEOUS</li>
 *   <li>{@link #TRACHEOSTOMY}: INTUBATED   &rarr; TRACHEOSTOMY</li>
 * </ul>
 *
 * <p>Any event applied to a patient who is not in the required status
 * (e.g. intubating an already-intubated patient, extubating a spontaneous one)
 * is illegal — see {@link #isAllowedFrom(RespiratoryStatus)} — and the service
 * rejects it without writing anything.
 */

public enum AirwayEventType {
    INTUBATION(RespiratoryStatus.SPONTANEOUS, RespiratoryStatus.INTUBATED),
    EXTUBATION(RespiratoryStatus.INTUBATED, RespiratoryStatus.SPONTANEOUS),
    TRACHEOSTOMY(RespiratoryStatus.INTUBATED, RespiratoryStatus.TRACHEOSTOMY);

    private final RespiratoryStatus requiredCurrentStatus;
    private final RespiratoryStatus resultingStatus;

    AirwayEventType(RespiratoryStatus requiredCurrentStatus, RespiratoryStatus resultingStatus) {
        this.requiredCurrentStatus = requiredCurrentStatus;
        this.resultingStatus = resultingStatus;
    }

    /** The status the patient must currently be in for this event to be legal. */
    public RespiratoryStatus requiredCurrentStatus() {
        return requiredCurrentStatus;
    }

    /** The status the patient transitions to once this event is applied. */
    public RespiratoryStatus resultingStatus() {
        return resultingStatus;
    }

    /** {@code true} iff this event is a legal transition from {@code current}. */
    public boolean isAllowedFrom(RespiratoryStatus current) {
        return this.requiredCurrentStatus == current;
    }
}
