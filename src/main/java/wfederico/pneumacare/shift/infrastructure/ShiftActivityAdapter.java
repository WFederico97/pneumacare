package wfederico.pneumacare.shift.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventRepository;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;
import wfederico.pneumacare.shift.application.ShiftActivityPort;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link ShiftActivityPort} adapter. Contains the only dependency from
 * {@code shift} on clinical/procedures persistence.
 *
 * <p>Three grouped queries per call, not three per shift.
 */
@Component
@RequiredArgsConstructor
public class ShiftActivityAdapter implements ShiftActivityPort {

    private final EvaluationRepository evaluations;
    private final AirwayEventRepository airwayEvents;
    private final SbtRepository sbts;

    @Override
    public Map<UUID, ShiftActivity> countByShiftIds(Collection<UUID> shiftIds) {
        if (shiftIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> evaluationCounts = evaluations.countByShiftIds(shiftIds).stream()
                .collect(Collectors.toMap(
                        EvaluationRepository.ShiftCount::getShiftId,
                        EvaluationRepository.ShiftCount::getTotal));
        Map<UUID, Long> airwayCounts = airwayEvents.countByShiftIds(shiftIds).stream()
                .collect(Collectors.toMap(
                        AirwayEventRepository.ShiftCount::getShiftId,
                        AirwayEventRepository.ShiftCount::getTotal));
        Map<UUID, Long> sbtCounts = sbts.countByShiftIds(shiftIds).stream()
                .collect(Collectors.toMap(
                        SbtRepository.ShiftCount::getShiftId,
                        SbtRepository.ShiftCount::getTotal));

        Map<UUID, ShiftActivity> result = new HashMap<>();
        for (UUID shiftId : shiftIds) {
            result.put(shiftId, new ShiftActivity(
                    evaluationCounts.getOrDefault(shiftId, 0L),
                    airwayCounts.getOrDefault(shiftId, 0L),
                    sbtCounts.getOrDefault(shiftId, 0L)));
        }
        return result;
    }
}
