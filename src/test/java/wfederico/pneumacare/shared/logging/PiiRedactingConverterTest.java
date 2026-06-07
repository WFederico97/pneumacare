package wfederico.pneumacare.shared.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiRedactingConverter")
class PiiRedactingConverterTest {

    private PiiRedactingConverter converter;
    private Logger logger;

    @BeforeEach
    void setUp() {
        converter = new PiiRedactingConverter();
        logger = (Logger) LoggerFactory.getLogger(PiiRedactingConverterTest.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LoggingEvent event(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setLoggerName(logger.getName());
        event.setMessage(message);
        event.setArgumentArray(new Object[0]);
        return event;
    }

    // ── DNI redaction ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("convert_8digitDni_replacedWithToken")
    void convert_8digitDni_replacedWithToken() {
        assertThat(converter.convert(event("Admitting patient with DNI 35123456")))
                .isEqualTo("Admitting patient with DNI [DNI-REDACTED]");
    }

    @Test
    @DisplayName("convert_7digitDni_replacedWithToken")
    void convert_7digitDni_replacedWithToken() {
        assertThat(converter.convert(event("dni=3512345 found")))
                .isEqualTo("dni=[DNI-REDACTED] found");
    }

    @Test
    @DisplayName("convert_multipleDniInMessage_allReplaced")
    void convert_multipleDniInMessage_allReplaced() {
        String result = converter.convert(event("patient1=35123456 patient2=7654321"));
        assertThat(result).isEqualTo("patient1=[DNI-REDACTED] patient2=[DNI-REDACTED]");
    }

    // ── Safe values must not be redacted ──────────────────────────────────────

    @Test
    @DisplayName("convert_uuidWithHyphens_notRedacted")
    void convert_uuidWithHyphens_notRedacted() {
        String uuid = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";
        String result = converter.convert(event("patientId=" + uuid));
        assertThat(result).isEqualTo("patientId=" + uuid);
    }

    @Test
    @DisplayName("convert_6digits_notRedacted")
    void convert_6digits_notRedacted() {
        // 6 digits is below the 7-digit minimum — must NOT be replaced
        assertThat(converter.convert(event("code=123456 processed")))
                .isEqualTo("code=123456 processed");
    }

    @Test
    @DisplayName("convert_9digits_notRedacted")
    void convert_9digits_notRedacted() {
        // 9 digits is above the 8-digit maximum — must NOT be replaced
        assertThat(converter.convert(event("ref=123456789 processed")))
                .isEqualTo("ref=123456789 processed");
    }

    @Test
    @DisplayName("convert_digitsEmbeddedInLongerNumber_notRedacted")
    void convert_digitsEmbeddedInLongerNumber_notRedacted() {
        // Word-boundary check: 7-digit run inside a 10-digit phone number must not match
        assertThat(converter.convert(event("phone=0123456789")))
                .isEqualTo("phone=0123456789");
    }

    @Test
    @DisplayName("convert_plainTextMessage_returnedUnchanged")
    void convert_plainTextMessage_returnedUnchanged() {
        assertThat(converter.convert(event("Patient admitted successfully")))
                .isEqualTo("Patient admitted successfully");
    }

    @Test
    @DisplayName("convert_nullFormattedMessage_returnsEmpty")
    void convert_nullFormattedMessage_returnsEmpty() {
        LoggingEvent nullMsgEvent = new LoggingEvent();
        nullMsgEvent.setLevel(Level.INFO);
        nullMsgEvent.setLoggerName(logger.getName());
        // message left null on purpose
        assertThat(converter.convert(nullMsgEvent)).isEmpty();
    }
}
