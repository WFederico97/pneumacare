package wfederico.pneumacare.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AssetAssignmentRepository extends JpaRepository<AssetAssignmentJpaEntity, UUID> {

    /** The active (unreleased) assignment for a ventilator, if any. */
    Optional<AssetAssignmentJpaEntity> findByVentilatorIdAndReleasedAtIsNull(UUID ventilatorId);

    /** The active (unreleased) assignment for a patient, if any. */
    Optional<AssetAssignmentJpaEntity> findByPatientIdAndReleasedAtIsNull(UUID patientId);

    /** True if the patient already holds an active assignment. */
    boolean existsByPatientIdAndReleasedAtIsNull(UUID patientId);

    /**
     * Patient existence check via native SQL: the patient JPA entity belongs to
     * the patient context, so this context deliberately avoids importing it
     * (same pattern as {@code PhysicalVentilatorRepository.icuExists}).
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM patients WHERE id = :patientId)", nativeQuery = true)
    boolean patientExists(@Param("patientId") UUID patientId);

    /**
     * The patient's ICU and whether their episode is still open, by the same
     * native-SQL convention as {@link #patientExists}. Empty when the patient
     * does not exist.
     *
     * <p>Needed so a ventilator cannot be assigned across ICUs, or to an episode
     * that has already been closed.
     */
    @Query(value = """
            SELECT icu_id AS icuId, (clinical_status = 'ADMITTED') AS episodeOpen
            FROM patients WHERE id = :patientId
            """, nativeQuery = true)
    Optional<PatientEpisodeRow> findPatientEpisode(@Param("patientId") UUID patientId);

    /** Projection for {@link #findPatientEpisode}. */
    interface PatientEpisodeRow {
        UUID getIcuId();
        boolean getEpisodeOpen();
    }
}
