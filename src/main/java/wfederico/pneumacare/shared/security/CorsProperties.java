package wfederico.pneumacare.shared.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Origins allowed to make cross-origin requests to this API.
     * Set via app.cors.allowed-origins in each profile's YAML,
     * or override with the CORS_ALLOWED_ORIGINS environment variable in staging/prod.
     */
    private List<String> allowedOrigins = new ArrayList<>();
}
