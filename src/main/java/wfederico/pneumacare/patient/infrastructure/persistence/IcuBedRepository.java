package wfederico.pneumacare.patient.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import wfederico.pneumacare.patient.domain.BedStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link IcuBedJpaEntity}.
 *
 * <p>The custom query {@link #findByIdAndIcu_Id} is used by the admission service
 * to atomically verify that the requested bed both exists <em>and</em> belongs to
 * the requested ICU in a single database round-trip.
 *
 * <p>The dashboard query filters by ICU and a bounded set of statuses,
 * leveraging the {@code icu_beds(icu_id)} index defined in migration V1.
 */
@Repository
public interface IcuBedRepository extends JpaRepository<IcuBedJpaEntity, UUID> {

    /**
     * Finds a bed by its own UUID and the UUID of the ICU it belongs to.
     * Returns {@link Optional#empty()} if the bed does not exist or belongs to a
     * different ICU — both conditions are treated as a request error by the
     * admission service.
     *
     * @param bedId the bed UUID supplied in the admission request
     * @param icuId the ICU UUID supplied in the admission request
     * @return the matching bed, or empty if not found in that ICU
     */
    Optional<IcuBedJpaEntity> findByIdAndIcu_Id(UUID bedId, UUID icuId);

    /**
     * Lists all beds for a given ICU filtered by the allowed dashboard statuses.
     *
     * @param icuId ICU UUID extracted from authenticated user claims
     * @param statuses allowed bed statuses for dashboard rendering
     * @return ordered bed list for that ICU and statuses
     */
    List<IcuBedJpaEntity> findByIcu_IdAndStatusInOrderByBedNumberAsc(UUID icuId, List<BedStatus> statuses);
}
