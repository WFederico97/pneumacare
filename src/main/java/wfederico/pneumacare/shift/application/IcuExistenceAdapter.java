package wfederico.pneumacare.shift.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuRepository;
import wfederico.pneumacare.shift.application.IcuExistencePort;

import java.util.UUID;

/**
 * {@link IcuExistencePort} adapter backed by the patient context's
 * {@code IcuRepository}. This is the <em>only</em> class in the shift context
 * that depends on patient persistence — the cross-context coupling is contained here.
 */
@Component
@RequiredArgsConstructor
public class IcuExistenceAdapter implements IcuExistencePort {
    private final IcuRepository icuRepository;

    @Override
    public boolean exists(UUID icuId){
        return icuRepository.existsById(icuId);
    }
}