package wfederico.pneumacare.clinical.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import wfederico.pneumacare.clinical.application.strategy.TecmeStrategy;
import wfederico.pneumacare.clinical.application.strategy.VentilatorFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class VentilatorFactoryTest {

    private VentilatorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new VentilatorFactory(new TecmeStrategy());
    }

    // ── Successful resolution ─────────────────────────────────────────────────

    @Test
    @DisplayName("resolve() — brand 'TECME' (uppercase) resolves to TecmeStrategy")
    void resolve_tecmeUppercase_returnsTecmeStrategy() {
        assertThat(factory.resolve("TECME"))
                .isInstanceOf(TecmeStrategy.class);
    }

    @Test
    @DisplayName("resolve() — brand 'tecme' (lowercase) resolves to TecmeStrategy")
    void resolve_tecmeLowercase_returnsTecmeStrategy() {
        assertThat(factory.resolve("tecme"))
                .isInstanceOf(TecmeStrategy.class);
    }

    // ── Error cases ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolve() — unknown brand throws IllegalArgumentException with brand in message")
    void resolve_unknownBrand_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.resolve("PUREMA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUREMA");
    }

    @Test
    @DisplayName("resolve() — null brand throws IllegalArgumentException")
    void resolve_nullBrand_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.resolve(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Performance ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolve() — TECME resolution completes within 50ms performance budget")
    void resolve_tecme_completesWithinPerformanceBudget() {
        assertTimeout(Duration.ofMillis(50),
                () -> factory.resolve("TECME"));
    }

}
