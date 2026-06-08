package wfederico.pneumacare.clinical.application.strategy;

import org.springframework.stereotype.Component;
import wfederico.pneumacare.clinical.domain.VentilatorBrand;

import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.UNKNOWN_BRAND_ERROR;

/**
 * Routes a brand identifier to the matching {@link VentilatorStrategy} instance.
 *
 * <p>The factory exposes two resolution APIs:
 * <ul>
 *   <li>{@link #resolve(VentilatorBrand)} — preferred, type-safe entry point used
 *       by the application layer once the request payload has been validated.</li>
 *   <li>{@link #resolve(String)} — case-insensitive convenience overload retained
 *       for callers that still receive raw strings (e.g. legacy integrations).</li>
 * </ul>
 *
 * <p>A {@code null} or unrecognised brand throws {@link IllegalArgumentException}
 * with a Spanish user-facing message defined in
 * {@link wfederico.pneumacare.shared.constants.ExceptionMessageConstants#UNKNOWN_BRAND_ERROR}.
 *
 * <p><b>Adding a new brand:</b>
 * <ol>
 *   <li>Add a new constant to {@link VentilatorBrand}.</li>
 *   <li>Create a new {@code @Component} implementing {@link VentilatorStrategy}.</li>
 *   <li>Add a constructor parameter and field of that type to this class.</li>
 *   <li>Add a {@code case} arm to {@link #resolve(VentilatorBrand)}.</li>
 * </ol>
 */
@Component
public class VentilatorFactory {
    private final TecmeStrategy tecmeStrategy;
    private final NeumoventStrategy neumoventStrategy;

    public VentilatorFactory(TecmeStrategy tecmeStrategy, NeumoventStrategy neumoventStrategy) {
        this.tecmeStrategy = tecmeStrategy;
        this.neumoventStrategy = neumoventStrategy;
    }

    /**
     * Resolves the strategy for the given ventilator brand (type-safe).
     *
     * @param brand canonical ventilator brand enum
     * @return the strategy instance registered for {@code brand}
     * @throws IllegalArgumentException if {@code brand} is {@code null} or unsupported
     */
    public VentilatorStrategy resolve(VentilatorBrand brand) {
        if (brand == null) {
            throw new IllegalArgumentException(UNKNOWN_BRAND_ERROR);
        }
        return switch (brand) {
            case TECME     -> tecmeStrategy;
            case NEUMOVENT -> neumoventStrategy;
        };
    }

    /**
     * Resolves the strategy for the given ventilator brand by name.
     *
     * @param brand ventilator brand identifier (case-insensitive,
     *              e.g. {@code "TECME"}, {@code "NEUMOVENT"})
     * @return the strategy instance registered for {@code brand}
     * @throws IllegalArgumentException if {@code brand} is {@code null} or unrecognised
     */
    public VentilatorStrategy resolve(String brand) {
        if (brand == null) {
            throw new IllegalArgumentException(UNKNOWN_BRAND_ERROR);
        }
        try {
            return resolve(VentilatorBrand.valueOf(brand.toUpperCase()));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(UNKNOWN_BRAND_ERROR + brand);
        }
    }
}
