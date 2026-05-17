package wfederico.backendjavacoretemplate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
        "org.springframework.boot.actuate.autoconfigure.metrics.export.datadog.DatadogMetricsExportAutoConfiguration"
})
public class BackendJavaCoreTemplateApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendJavaCoreTemplateApplication.class, args);
    }

}
