-- =============================================================================
-- V18__create_and_seed_medical_reference.sql
-- PneumaCare - Medical reference knowledge base for the clinical consultant.
--
-- Curated, non-PII weaning/respiratory guidance keyed by (metric, band), where
-- band is the interpretation enum constant produced by the clinical classifiers.
-- Seeded with the clinically-actionable (concern) bands only; fully-normal
-- evaluations intentionally match nothing and receive the safe default in code.
-- =============================================================================

CREATE TABLE medical_reference (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    metric           VARCHAR(20)  NOT NULL,
    band             VARCHAR(50)  NOT NULL,
    range_descriptor VARCHAR(100) NOT NULL,
    context          VARCHAR(100) NOT NULL,
    guidance_text    TEXT         NOT NULL,
    source_ref       VARCHAR(255) NOT NULL,
    priority         INT          NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    CONSTRAINT pk_medical_reference             PRIMARY KEY (id),
    CONSTRAINT uq_medical_reference_metric_band UNIQUE (metric, band),
    CONSTRAINT ck_medical_reference_metric      CHECK (metric IN ('RSBI', 'PAFI', 'CSTAT'))
);

COMMENT ON TABLE medical_reference IS
    'Curated non-PII clinical guidance keyed by (metric, interpretation band) for the DB-backed consultant.';

INSERT INTO medical_reference (metric, band, range_descriptor, context, guidance_text, source_ref, priority)
VALUES
    ('PAFI', 'SEVERE_ARDS', '< 100 mmHg', 'oxygenation / ARDS',
     'A PaO2/FiO2 ratio below 100 mmHg meets the Berlin criteria for severe ARDS; prioritize lung-protective ventilation and consider prone positioning and specialist review before any weaning attempt.',
     'ARDS Definition Task Force (Berlin), JAMA 2012;307:2526-33', 100),

    ('PAFI', 'MODERATE_ARDS', '100-200 mmHg', 'oxygenation / ARDS',
     'A PaO2/FiO2 ratio of 100-200 mmHg indicates moderate ARDS; maintain lung-protective settings, optimize PEEP, and defer spontaneous breathing trials until oxygenation improves.',
     'ARDS Definition Task Force (Berlin), JAMA 2012;307:2526-33', 80),

    ('RSBI', 'UNFAVORABLE', '> 105', 'weaning readiness',
     'An RSBI above 105 predicts a high likelihood of weaning failure; defer the spontaneous breathing trial and reassess respiratory drive, load, and sedation.',
     'Yang & Tobin, N Engl J Med 1991;324:1445-50', 70),

    ('PAFI', 'MILD_ARDS', '200-300 mmHg', 'oxygenation / ARDS',
     'A PaO2/FiO2 ratio of 200-300 mmHg indicates mild ARDS; continue protective ventilation and reassess the oxygenation trend before escalating weaning.',
     'ARDS Definition Task Force (Berlin), JAMA 2012;307:2526-33', 60),

    ('CSTAT', 'LOW', '< 50 mL/cmH2O', 'lung mechanics',
     'A static compliance below 50 mL/cmH2O reflects reduced respiratory-system compliance; review for atelectasis, edema, or over-distension and reassess PEEP and tidal volume.',
     'Grinnan & Truwit, Crit Care 2005;9:472-84', 50),

    ('PAFI', 'AT_RISK', '300-400 mmHg', 'oxygenation',
     'A PaO2/FiO2 ratio of 300-400 mmHg is below the normal range and warrants monitoring of oxygenation before proceeding with weaning.',
     'ARDS Definition Task Force (Berlin), JAMA 2012;307:2526-33', 40),

    ('RSBI', 'BORDERLINE', '80-105', 'weaning readiness',
     'An RSBI between 80 and 105 is borderline for weaning tolerance; proceed with a closely monitored spontaneous breathing trial and be prepared to abort on signs of fatigue.',
     'Yang & Tobin, N Engl J Med 1991;324:1445-50', 30);
