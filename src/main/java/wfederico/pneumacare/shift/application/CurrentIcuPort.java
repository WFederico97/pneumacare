package wfederico.pneumacare.shift.application;

import java.util.UUID;

/**
 * Outbound port: resolves the ICU UUID for the current request's context.
 *
 * <p>Authentication is not yet implemented (separate backlog USs). Until then the
 * adapter returns a configured default. When auth lands, only the adapter changes —
 * it will read the {@code icu_id} JWT claim — while this port and its callers stay the same.
 */
public interface CurrentIcuPort {
    UUID currentIcuId();
}
