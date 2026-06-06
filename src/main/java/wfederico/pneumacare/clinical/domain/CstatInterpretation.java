package wfederico.pneumacare.clinical.domain;

public enum CstatInterpretation {
    HIGH,
    NORMAL,
    LOW;

    private static final double HIGH_LOWER = 100.0;
    private static final double NORMAL_LOWER = 50.0;

    public static CstatInterpretation from(double cstat){
        if (cstat >= HIGH_LOWER) return HIGH;
        if (cstat >= NORMAL_LOWER) return NORMAL;
        return LOW;
    };
}
