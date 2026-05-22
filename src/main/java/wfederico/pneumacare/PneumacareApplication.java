package wfederico.pneumacare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
        "org.springframework.boot.actuate.autoconfigure.metrics.export.datadog.DatadogMetricsExportAutoConfiguration"
})
public class PneumacareApplication {

    public static void main(String[] args) {
        SpringApplication.run(PneumacareApplication.class, args);
    }

}
