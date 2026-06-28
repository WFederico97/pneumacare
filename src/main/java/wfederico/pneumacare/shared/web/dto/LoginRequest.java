package wfederico.pneumacare.shared.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Login credentials. Both fields are required. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
