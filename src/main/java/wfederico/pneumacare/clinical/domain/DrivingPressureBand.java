package wfederico.pneumacare.clinical.domain;

/**
 * Driving-pressure band for the ventilator's static driving pressure
 * (ΔP = Pplat − PEEPtotal, in cmH₂O).
 *
 * <p>Driving pressure is the ventilator variable most strongly associated with
 * survival in ARDS: a ΔP above ~15 cmH₂O is linked to increased mortality even
 * when tidal volume and plateau pressure are within conventional limits.
 *
 * <p>Reference: Amato MBP et&nbsp;al., "Driving Pressure and Survival in the
 * Acute Respiratory Distress Syndrome", N&nbsp;Engl&nbsp;J&nbsp;Med
 * 2015;372:747-55.
 */
public enum DrivingPressureBand {
    PROTECTIVE,
    HIGH;

    /** ΔP above this many cmH₂O is associated with increased mortality. */
    private static final double PROTECTIVE_UPPER = 15.0;

    /**
     * Classifies a driving pressure into its band.
     *
     * @param drivingPressure ΔP = Pplat − PEEPtotal, in cmH₂O
     * @return {@link #HIGH} when ΔP exceeds 15 cmH₂O, otherwise {@link #PROTECTIVE}
     */
    public static DrivingPressureBand from(double drivingPressure) {
        return drivingPressure > PROTECTIVE_UPPER ? HIGH : PROTECTIVE;
    }
}
