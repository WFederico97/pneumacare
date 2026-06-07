package wfederico.pneumacare.shared.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Logback message converter that redacts bare DNI-shaped digit sequences from
 * formatted log messages before they are written to any appender that uses it.
 *
 * <h2>What it catches</h2>
 * Any standalone run of 7 or 8 digits surrounded by word boundaries
 * (i.e. not part of a longer number such as a phone number or timestamp).
 * Matching sequences are replaced with the literal token {@code [DNI-REDACTED]}.
 *
 * <h2>Registration</h2>
 * Registered in {@code logback-spring.xml} as the conversion word {@code safeMsg}:
 * <pre>
 *   &lt;conversionRule conversionWord="safeMsg"
 *                   converterClass="wfederico.pneumacare.shared.logging.PiiRedactingConverter"/&gt;
 * </pre>
 * Use {@code %safeMsg} instead of {@code %msg} in your appender's pattern.
 *
 * <h2>Scope and limitations</h2>
 * This converter is a <strong>last-resort safety net</strong>. It applies only to
 * appenders whose pattern includes {@code %safeMsg}. The
 * {@code OpenTelemetryAppender} uses {@code getFormattedMessage()} directly and
 * therefore bypasses this converter — PII protection for OTel relies on
 * {@link PiiMaskingUtil} being used explicitly in log statements.
 */
public class PiiRedactingConverter extends ClassicConverter {

    /**
     * Matches standalone 7-or-8-digit sequences (word-boundary anchored).
     * Does not match digits that are part of longer numeric strings
     * (e.g. {@code 123456789} is 9 digits and will not match).
     */
    static final Pattern DNI_PATTERN = Pattern.compile("\\b\\d{7,8}\\b");

    static final String DNI_REPLACEMENT = "[DNI-REDACTED]";

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) {
            return "";
        }
        return DNI_PATTERN.matcher(message).replaceAll(DNI_REPLACEMENT);
    }
}
