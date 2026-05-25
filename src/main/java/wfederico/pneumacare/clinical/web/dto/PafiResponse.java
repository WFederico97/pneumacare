package wfederico.pneumacare.clinical.web.dto;

/**
 * Result of a PaO₂/FiO₂ ratio (PaFi) calculation.
 *
 * <p>Classification follows the <em>Berlin Definition</em> of ARDS (2012):
 * <ul>
 *   <li>&ge; 400 mmHg → {@code NORMAL}</li>
 *   <li>300–399 mmHg  → {@code AT_RISK}</li>
 *   <li>200–299 mmHg  → {@code MILD_ARDS}</li>
 *   <li>100–199 mmHg  → {@code MODERATE_ARDS}</li>
 *   <li>&lt; 100 mmHg → {@code SEVERE_ARDS}</li>
 * </ul>
 */
public record PafiResponse(

        /** Calculated PaO₂/FiO₂ ratio in mmHg. */
        double pafi,

        /** ARDS severity classification based on PaFi value. */
        String classification
) {}
