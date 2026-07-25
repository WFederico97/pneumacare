package wfederico.pneumacare.inventory.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A patient's current (unreleased) ventilator assignment, with the ventilator serial. */
public record ActiveAssignmentResponse(UUID ventilatorId, String serialNumber, OffsetDateTime assignedAt) {
}
