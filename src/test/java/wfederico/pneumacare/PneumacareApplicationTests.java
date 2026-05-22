package wfederico.pneumacare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false"
})
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Requires full infrastructure (PostgreSQL, Redis, OAuth2 provider)")
class PneumacareApplicationTests {

    @Test
    void contextLoads() {
    }

}
