package wfederico.pneumacare.config.domain;

/**
 * Groups system settings in the centralized configuration hub so the admin UI can
 * render them under coherent sections.
 */
public enum SettingCategory {
    /** General system-wide parameters (e.g. default analytics window, ICU display name). */
    SYSTEM,
    /** Clinical decision rules and alert thresholds. */
    CLINICAL_RULES,
    /** Hardware / equipment defaults (e.g. default ventilator brand). */
    HARDWARE,
    /** Outbound notification behaviour (e.g. alert channel toggles). */
    NOTIFICATIONS
}
