package wfederico.pneumacare.shared.web;

import java.time.Instant;

public record HealthStatusResponse(String status, Instant timestamp) {

    public static HealthStatusResponse up() {
        return new HealthStatusResponse("UP", Instant.now());
    }
}
