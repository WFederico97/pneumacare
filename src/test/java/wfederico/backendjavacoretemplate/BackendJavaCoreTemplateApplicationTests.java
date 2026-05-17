package wfederico.backendjavacoretemplate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Requires full infrastructure (Kafka, OAuth2 provider)")
class BackendJavaCoreTemplateApplicationTests {

    @Test
    void contextLoads() {
    }

}
