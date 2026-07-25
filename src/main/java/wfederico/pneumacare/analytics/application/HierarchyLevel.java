package wfederico.pneumacare.analytics.application;

/** Aggregation level for the multi-level analytics rollup. */
public enum HierarchyLevel {
    /** Rollup by province (top of the org hierarchy). */
    PROVINCE,
    /** Rollup by institution / hospital. */
    INSTITUTION,
    /** One row per admitted patient (leaf level). */
    PATIENT
}
