package wfederico.pneumacare.shared.telemetry;

import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * {@link BeanPostProcessor} that transparently wraps every {@link SpanExporter}
 * bean with {@link PiiSanitizingSpanExporter} after the bean is initialised.
 *
 * <p>This approach is chosen over {@code @Primary} / qualifier injection to
 * avoid circular-dependency problems with Spring Boot's OTel auto-configuration,
 * which creates the {@code OtlpHttpSpanExporter} bean internally. The post-
 * processor runs after bean initialisation, before the bean is registered in
 * the {@code SdkTracerProvider}, so the provider always receives the sanitizing
 * wrapper as the effective exporter.
 *
 * <p>The check {@code !(bean instanceof PiiSanitizingSpanExporter)} prevents
 * double-wrapping in case the post-processor runs more than once (e.g., proxy
 * re-creation during context refresh).
 */
@Component
public class PiiSpanExporterBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SpanExporter exporter
                && !(bean instanceof PiiSanitizingSpanExporter)) {
            return new PiiSanitizingSpanExporter(exporter);
        }
        return bean;
    }
}
