package wfederico.pneumacare.clinical.domain;

public enum RsbiInterpretation {
    FAVORABLE,
    BORDERLINE,
    UNFAVORABLE;

    private static final double FAVORABLE_UPPER = 80.0;
    private static final double BORDERLINE_UPPER = 105.0;

    /**
     * Classifies a calculated RSBI value into a weaning outcome category.
     * <p>Reference: Yang & Tobin (1991), N Engl J Med 324(21):1445–50.
     */

    public static RsbiInterpretation from(double rsbi){
        if (rsbi < FAVORABLE_UPPER) return FAVORABLE;
        if (rsbi <= BORDERLINE_UPPER) return BORDERLINE;
        return UNFAVORABLE;
    }
}
