package wfederico.pneumacare.clinical.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import wfederico.pneumacare.clinical.application.strategy.NeumoventStrategy;
import wfederico.pneumacare.clinical.application.strategy.TecmeStrategy;
import wfederico.pneumacare.clinical.application.strategy.VentilatorFactory;
import wfederico.pneumacare.clinical.domain.VentilatorBrand;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class VentilatorFactoryTest {

    private VentilatorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new VentilatorFactory(new TecmeStrategy(), new NeumoventStrategy());
    }

    // ── Successful resolution — String overload ───────────────────────────────

    @Test
    @DisplayName("resolve(String) — brand 'TECME' (uppercase) resolves to TecmeStrategy")
    void resolve_tecmeUppercase_returnsTecmeStrategy() {
        assertThat(factory.resolve("TECME"))
                .isInstanceOf(TecmeStrategy.class);
    }

    @Test
    @DisplayName("resolve(String) — brand 'tecme' (lowercase) resolves to TecmeStrategy")
    void resolve_tecmeLowercase_returnsTecmeStrategy() {
        assertThat(factory.resolve("tecme"))
                .isInstanceOf(TecmeStrategy.class);
    }

    @Test
    @DisplayName("resolve(String) — brand 'NEUMOVENT' (uppercase) resolves to NeumoventStrategy")
    void resolve_neumoventUppercase_returnsNeumoventStrategy() {
        assertThat(factory.resolve("NEUMOVENT"))
                .isInstanceOf(NeumoventStrategy.class);
    }

    @Test
    @DisplayName("resolve(String) — brand 'neumovent' (lowercase) resolves to NeumoventStrategy")
    void resolve_neumoventLowercase_returnsNeumoventStrategy() {
        assertThat(factory.resolve("neumovent"))
                .isInstanceOf(NeumoventStrategy.class);
    }

    // ── Successful resolution — VentilatorBrand overload ──────────────────────

    @Test
    @DisplayName("resolve(VentilatorBrand) — TECME enum resolves to TecmeStrategy")
    void resolve_tecmeBrand_returnsTecmeStrategy() {
        assertThat(factory.resolve(VentilatorBrand.TECME))
                .isInstanceOf(TecmeStrategy.class);
    }

    @Test
    @DisplayName("resolve(VentilatorBrand) — NEUMOVENT enum resolves to NeumoventStrategy")
    void resolve_neumoventBrand_returnsNeumoventStrategy() {
        assertThat(factory.resolve(VentilatorBrand.NEUMOVENT))
                .isInstanceOf(NeumoventStrategy.class);
    }

    // ── Error cases ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolve(String) — unknown brand throws IllegalArgumentException with brand in message")
    void resolve_unknownBrand_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.resolve("PUREMA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUREMA");
    }

    @Test
    @DisplayName("resolve(String) — null brand throws IllegalArgumentException")
    void resolve_nullStringBrand_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.resolve((String) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resolve(VentilatorBrand) — null brand throws IllegalArgumentException")
    void resolve_nullEnumBrand_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.resolve((VentilatorBrand) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Performance ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolve(String) — TECME resolution completes within 50ms performance budget")
    void resolve_tecme_completesWithinPerformanceBudget() {
        assertTimeout(Duration.ofMillis(50),
                () -> factory.resolve("TECME"));
    }

}
