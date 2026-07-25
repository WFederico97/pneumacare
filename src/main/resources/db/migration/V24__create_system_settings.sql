-- =============================================================================
-- V20__create_system_settings.sql
-- PneumaCare - Centralized configuration hub. A typed key/value catalog of
-- system-wide parameters, clinical rules, hardware defaults and notification
-- toggles, editable by administrators from a single admin panel.
-- =============================================================================

CREATE TABLE system_settings (
    setting_key VARCHAR(100) NOT NULL,
    value       VARCHAR(500) NOT NULL,
    category    VARCHAR(30)  NOT NULL,
    label       VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    value_type  VARCHAR(20)  NOT NULL,
    editable    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT pk_system_settings PRIMARY KEY (setting_key),
    CONSTRAINT chk_system_settings_category
        CHECK (category IN ('SYSTEM', 'CLINICAL_RULES', 'HARDWARE', 'NOTIFICATIONS')),
    CONSTRAINT chk_system_settings_value_type
        CHECK (value_type IN ('text', 'number', 'boolean'))
);

COMMENT ON TABLE system_settings IS
    'Centralized configuration hub: system-wide typed key/value settings.';

INSERT INTO system_settings (setting_key, value, category, label, description, value_type, editable) VALUES
    ('analytics.window.default.days', '14', 'SYSTEM', 'Ventana de analítica por defecto (días)',
        'Rango de fechas inicial para los paneles de analítica.', 'number', TRUE),
    ('app.icu.display.name', 'UCI Central', 'SYSTEM', 'Nombre visible de la UCI',
        'Etiqueta mostrada en el tablero y los reportes.', 'text', TRUE),
    ('alert.rsbi.threshold', '105', 'CLINICAL_RULES', 'Umbral RSBI de alerta',
        'RSBI por encima de este valor marca destete desfavorable.', 'number', TRUE),
    ('alert.pafi.critical', '100', 'CLINICAL_RULES', 'PaFi crítico',
        'PaFi por debajo de este valor indica SDRA severo.', 'number', TRUE),
    ('ventilator.default.brand', 'TECME', 'HARDWARE', 'Marca de ventilador por defecto',
        'Marca preseleccionada al abrir el formulario de evaluación.', 'text', TRUE),
    ('alert.telegram.enabled', 'true', 'NOTIFICATIONS', 'Alertas por Telegram',
        'Habilita el envío de alertas clínicas al canal de Telegram.', 'boolean', TRUE);
