package wfederico.pneumacare.shift.application;

import java.util.UUID;

/**
 * Outbound port: checks whether an ICU exists, without coupling the shift
 * context to the patient context's persistence. Implemented in infrastructure.
 */
public interface IcuExistencePort {
    boolean exists(UUID icuId);
}
