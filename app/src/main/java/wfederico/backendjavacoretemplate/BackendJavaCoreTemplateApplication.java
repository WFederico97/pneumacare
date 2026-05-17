package wfederico.backendjavacoretemplate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 * All modules on the classpath are auto-configured by Spring Boot.
 * To disable a platform feature at runtime, exclude its auto-configuration class
 * or remove the corresponding platform module from app/pom.xml.
 */
@SpringBootApplication(excludeName = {
        "org.springframework.boot.actuate.autoconfigure.metrics.export.datadog.DatadogMetricsExportAutoConfiguration"
})
public class BackendJavaCoreTemplateApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendJavaCoreTemplateApplication.class, args);
    }
}

