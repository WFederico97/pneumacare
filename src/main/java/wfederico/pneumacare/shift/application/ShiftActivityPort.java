package wfederico.pneumacare.shift.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound port over the clinical work recorded during a shift. Keeps evaluation,
 * airway-event and SBT persistence types out of the shift application layer.
 *
 * <p>Counts are fetched for a whole page of shifts at once (one grouped query per
 * activity kind) rather than per shift, so rendering the history stays a fixed
 * number of queries regardless of how many shifts are listed.
 */
public interface ShiftActivityPort {

    /** Activity counts keyed by shift id; shifts with no activity may be absent. */
    Map<UUID, ShiftActivity> countByShiftIds(Collection<UUID> shiftIds);

    /** Clinical activity recorded against one shift. */
    record ShiftActivity(long evaluations, long airwayEvents, long sbts) {
        public static final ShiftActivity NONE = new ShiftActivity(0, 0, 0);
    }
}
