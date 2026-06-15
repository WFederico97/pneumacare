package wfederico.pneumacare.procedures.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: resolves the currently OPEN shift for an ICU.
 *
 * <p>Airway events must be attached to an OPEN shift ; the shift id
 * is derived server-side, never sent by the client. Empty means the ICU has no
 * OPEN shift.
 */
public interface ActiveShiftPort {
    Optional<UUID> findActiveShiftId(UUID icuId);
}
