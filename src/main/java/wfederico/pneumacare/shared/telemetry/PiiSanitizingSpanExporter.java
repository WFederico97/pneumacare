package wfederico.pneumacare.shared.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link SpanExporter} decorator that redacts span attributes whose key names
 * match known PII patterns before the span is forwarded to the downstream
 * exporter (e.g., OTLP/HTTP).
 *
 * <p><b>Compliance:</b> Law 25.326 — no personally-identifiable information may
 * leave the JVM boundary in telemetry payloads. This exporter is the last line
 * of defence; application code should never add PII to spans in the first place.
 *
 * <p><b>Architecture note:</b> This class is registered via
 * {@link PiiSpanExporterBeanPostProcessor}, which wraps every
 * {@link SpanExporter} bean produced by Spring Boot's OTel auto-configuration
 * without requiring changes to the auto-configured {@code SdkTracerProvider}.
 *
 * <p><b>Performance:</b> Only copies {@link Attributes} when at least one key
 * is sensitive; clean spans are forwarded as-is.
 */
public final class PiiSanitizingSpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(PiiSanitizingSpanExporter.class);

    /** Replacement value written over any sensitive attribute. */
    static final String REDACTED_VALUE = "[REDACTED]";

    /**
     * Case-insensitive substrings that flag an attribute key as sensitive.
     * Extend this set as new domain fields are introduced.
     */
    private static final Set<String> SENSITIVE_SUBSTRINGS = Set.of(
            "password", "passwd", "pwd",
            "secret",   "token",  "credential",
            "authorization", "cookie", "apikey", "api_key",
            "nationalid",    "national_id", "dni", "cuit", "cuil", "ssn",
            "email",    "mail",
            "phone",    "telefono", "celular",
            "nombre",   "apellido",
            "firstname", "lastname", "fullname"
    );

    private final SpanExporter delegate;

    public PiiSanitizingSpanExporter(SpanExporter delegate) {
        this.delegate = delegate;
    }

    // ── SpanExporter contract ────────────────────────────────────────────────

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(
                spans.stream()
                        .map(this::sanitize)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    // ── PII scrubbing ────────────────────────────────────────────────────────

    private SpanData sanitize(SpanData original) {
        Attributes sanitized = sanitizeAttributes(original.getAttributes());
        return sanitized == original.getAttributes()
                ? original
                : new SanitizedSpanData(original, sanitized);
    }

    private Attributes sanitizeAttributes(Attributes original) {
        boolean[] modified = {false};
        AttributesBuilder builder = Attributes.builder();

        original.forEach((key, value) -> {
            if (isSensitive(key.getKey())) {
                log.debug("PII sanitizer: redacting span attribute '{}'", key.getKey());
                builder.put(AttributeKey.stringKey(key.getKey()), REDACTED_VALUE);
                modified[0] = true;
            } else {
                copyAttribute(builder, key, value);
            }
        });

        return modified[0] ? builder.build() : original;
    }

    /** Returns {@code true} if the key name contains a known PII substring (case-insensitive). */
    static boolean isSensitive(String keyName) {
        String lower = keyName.toLowerCase(Locale.ROOT);
        return SENSITIVE_SUBSTRINGS.stream().anyMatch(lower::contains);
    }

    @SuppressWarnings("unchecked")
    private static <T> void copyAttribute(AttributesBuilder builder, AttributeKey<T> key, Object value) {
        builder.put(key, (T) value);
    }

    // ── SpanData wrapper ─────────────────────────────────────────────────────

    /**
     * Thin {@link DelegatingSpanData} subclass that substitutes sanitized
     * {@link Attributes} while delegating every other method to the original.
     *
     * <p>Using {@code DelegatingSpanData} (the SDK's own extension point) ensures
     * that new methods added to the {@link SpanData} interface in future OTel SDK
     * versions are automatically forwarded without any changes here.
     */
    private static final class SanitizedSpanData extends DelegatingSpanData {

        private final Attributes sanitizedAttributes;

        SanitizedSpanData(SpanData delegate, Attributes sanitizedAttributes) {
            super(delegate);
            this.sanitizedAttributes = sanitizedAttributes;
        }

        /** Returns the PII-scrubbed attributes instead of the original ones. */
        @Override
        public Attributes getAttributes() {
            return sanitizedAttributes;
        }
    }
}
