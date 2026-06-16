package wfederico.pneumacare.procedures.application;

import wfederico.pneumacare.patient.domain.RespiratoryStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port over the patient aggregate, exposing only what airway-event
 * processing needs: the patient's ICU + current respiratory status, and a way to
 * apply a new respiratory status. Keeps {@code PatientJpaEntity} out of the
 * procedures application layer.
 */
public interface PatientAirwayPort {

    /** A patient's ICU and current airway state; empty if the patient does not exist. */
    Optional<PatientAirwayView> findAirwayView(UUID patientId);

    /** Persists the patient's new respiratory status. */
    void applyRespiratoryStatus(UUID patientId, RespiratoryStatus newStatus);

    /** Minimal read model for airway-event processing. */
    record PatientAirwayView(UUID patientId, UUID icuId, RespiratoryStatus respiratoryStatus) {}
}
