package wfederico.pneumacare.clinical.web.dto;

/**
 * Result of a Rapid Shallow Breathing Index (RSBI) calculation.
 *
 * <p>RSBI = Respiratory Rate / Tidal Volume (L)
 *
 * <ul>
 *   <li>&lt; 80  → {@code FAVORABLE}   — extubation likely to succeed</li>
 *   <li>80–105  → {@code BORDERLINE}   — proceed with caution</li>
 *   <li>&gt; 105 → {@code UNFAVORABLE} — extubation unlikely to succeed</li>
 * </ul>
 */
public record RsbiResponse(

        /** Calculated RSBI value (breaths/min per litre). */
        double rsbi,

        /** Clinical classification based on RSBI value. */
        String interpretation
) {}
