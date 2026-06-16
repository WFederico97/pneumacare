package wfederico.pneumacare.shift.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for submitting a shift handover note:
 * {@code POST /api/v1/shifts/{id}/handovers}.
 *
 * <p>Only {@code notesContent} is accepted from the client. {@code shiftId} comes
 * from the path; {@code authorId} is derived from the authenticated principal; and
 * the timestamp is set server-side — none are part of this contract.
 *
 * <p>Content validation (non-empty, max 4000 chars) is enforced in the service and
 * returns {@code 422}, per the ticket's acceptance criteria — so a missing or empty
 * value is {@code 422} rather than {@code 400}.
 */
public record CreateHandoverRequest(

        @Schema(description = "Handover note content (non-empty, max 4000 characters).",
                example = "Cama 3 estable, destete en curso. Cama 5 requiere control de sedación.")
        String notesContent
) {}
