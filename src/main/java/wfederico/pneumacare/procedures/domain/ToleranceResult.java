package wfederico.pneumacare.procedures.domain;

/**
 * Outcome of a Spontaneous Breathing Trial (SBT) — whether the patient tolerated
 * breathing on their own.
 *
 * <p>Stored as {@code VARCHAR(20)} in {@code spontaneous_breathing_trials.outcome}
 * via {@code @Enumerated(EnumType.STRING)}.
 *
 * <ul>
 *   <li>{@link #SUCCESS} — the patient tolerated the trial.</li>
 *   <li>{@link #FAILURE} — the patient did not tolerate the trial (a valid,
 *       clinically meaningful recorded outcome).</li>
 * </ul>
 */
public enum ToleranceResult {
    SUCCESS,
    FAILURE
}
