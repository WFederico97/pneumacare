package wfederico.pneumacare.clinical.domain;

public enum PafiClassification {
    NORMAL,
    AT_RISK,
    MILD_ARDS,
    MODERATE_ARDS,
    SEVERE_ARDS;

    private static final double NORMAL_LOWER = 400.0;
    private static final double AT_RISK_LOWER = 300.0;
    private static final double MILD_ARDS_LOWER = 200.0;
    private static final double MODERATE_ARDS_LOWER = 100.0;

    public static PafiClassification from(double pafi){
        if (pafi >= NORMAL_LOWER) return NORMAL;
        if (pafi >= AT_RISK_LOWER) return AT_RISK;
        if (pafi >= MILD_ARDS_LOWER) return MILD_ARDS;
        if (pafi >= MODERATE_ARDS_LOWER) return MODERATE_ARDS;
        return  SEVERE_ARDS;
    }
}
