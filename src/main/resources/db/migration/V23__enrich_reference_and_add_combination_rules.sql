-- =============================================================================
-- V23__enrich_reference_and_add_combination_rules.sql
-- PneumaCare - Evidence-grounded upgrade of the clinical consultant.
--
-- Two changes, both non-PII and reference-only:
--   1. Enrich the existing single-metric PaFi rows with the Berlin-cohort
--      hospital-mortality figures (mild 27% / moderate 32% / severe 45%),
--      reported in the same Berlin Definition paper already cited.
--   2. Add a cross-metric knowledge base (clinical_combination_rule): guidance
--      that only applies when *several* indices co-occur (e.g. a favorable RSBI
--      but ARDS-range oxygenation), which single-metric rows cannot express.
--
-- The consultant composes combination guidance first (whole-patient synthesis),
-- then falls back to the single-metric rows. Concern-only, safe-default when
-- nothing matches. No hosted or trained model — deterministic and fully citeable.
-- =============================================================================

-- 1. Enrich existing PaFi guidance with Berlin-cohort mortality context. ------
--    Source paper unchanged; it reported these mortality rates by severity.

UPDATE medical_reference
SET guidance_text = 'A PaO2/FiO2 ratio below 100 mmHg meets the Berlin criteria for severe ARDS '
        || '(Berlin-cohort hospital mortality ~45%); prioritize lung-protective ventilation and '
        || 'consider prone positioning and specialist review before any weaning attempt.',
    updated_at = now()
WHERE metric = 'PAFI' AND band = 'SEVERE_ARDS';

UPDATE medical_reference
SET guidance_text = 'A PaO2/FiO2 ratio of 100-200 mmHg indicates moderate ARDS '
        || '(Berlin-cohort hospital mortality ~32%); maintain lung-protective settings, optimize PEEP, '
        || 'and defer spontaneous breathing trials until oxygenation improves.',
    updated_at = now()
WHERE metric = 'PAFI' AND band = 'MODERATE_ARDS';

UPDATE medical_reference
SET guidance_text = 'A PaO2/FiO2 ratio of 200-300 mmHg indicates mild ARDS '
        || '(Berlin-cohort hospital mortality ~27%); continue protective ventilation and reassess the '
        || 'oxygenation trend before escalating weaning.',
    updated_at = now()
WHERE metric = 'PAFI' AND band = 'MILD_ARDS';

-- 2. Cross-metric knowledge base. --------------------------------------------
--    Each *_band column is a wildcard when NULL, otherwise a comma-separated
--    allow-list of interpretation enum constants; a rule fires only when every
--    non-null column matches the evaluation's corresponding band. dp_band is
--    the driving-pressure band (Pplat - PEEP): PROTECTIVE (<=15) / HIGH (>15).

CREATE TABLE clinical_combination_rule (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    rule_name     VARCHAR(80)  NOT NULL,
    rsbi_band     VARCHAR(60),
    pafi_band     VARCHAR(60),
    cstat_band    VARCHAR(60),
    dp_band       VARCHAR(30),
    guidance_text TEXT         NOT NULL,
    source_ref    VARCHAR(500) NOT NULL,
    priority      INT          NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT pk_clinical_combination_rule      PRIMARY KEY (id),
    CONSTRAINT uq_clinical_combination_rule_name UNIQUE (rule_name),
    CONSTRAINT ck_ccr_at_least_one_band CHECK (
        rsbi_band IS NOT NULL OR pafi_band IS NOT NULL
            OR cstat_band IS NOT NULL OR dp_band IS NOT NULL)
);

COMMENT ON TABLE clinical_combination_rule IS
    'Curated non-PII cross-metric guidance; fires when several interpretation bands co-occur.';

INSERT INTO clinical_combination_rule
    (rule_name, rsbi_band, pafi_band, cstat_band, dp_band, guidance_text, source_ref, priority)
VALUES
    ('severe-ards-stiff-lung', NULL, 'SEVERE_ARDS', 'LOW', NULL,
     'Severe hypoxemia together with low static compliance is a typical severe-ARDS mechanical profile '
       || '(Berlin-cohort hospital mortality ~45%); maintain lung-protective ventilation, consider prone '
       || 'positioning, and obtain specialist review before any weaning attempt.',
     'ARDS Definition Task Force (Berlin), JAMA 2012;307:2526-33', 210),

    ('high-driving-pressure', NULL, NULL, NULL, 'HIGH',
     'Driving pressure exceeds 15 cmH2O — the ventilator parameter most strongly associated with mortality '
       || 'in ARDS; reduce tidal volume and/or optimize PEEP to lower driving pressure toward a lung-protective target.',
     'Amato et al., N Engl J Med 2015;372:747-55', 205),

    ('oxygenation-gates-weaning', 'FAVORABLE', 'MODERATE_ARDS,SEVERE_ARDS', NULL, NULL,
     'Although the RSBI is favorable, oxygenation meets ARDS criteria; oxygenation — not respiratory drive — '
       || 'is the limiting factor, so defer the spontaneous breathing trial and prioritize lung-protective ventilation.',
     'ARDS Definition Task Force (Berlin), JAMA 2012;307:2526-33; Yang & Tobin, N Engl J Med 1991;324:1445-50', 200),

    ('high-load-stiff-lung', 'UNFAVORABLE', NULL, 'LOW', NULL,
     'A high RSBI together with reduced static compliance points to a combined high respiratory load and stiff '
       || 'lung; defer the spontaneous breathing trial and evaluate for atelectasis, edema, or over-distension '
       || 'while optimizing PEEP and tidal volume.',
     'Yang & Tobin, N Engl J Med 1991;324:1445-50; Grinnan & Truwit, Crit Care 2005;9:472-84', 180),

    ('weaning-readiness', 'FAVORABLE', 'NORMAL,AT_RISK', 'NORMAL,HIGH', NULL,
     'RSBI, oxygenation, and compliance are jointly consistent with weaning readiness; a protocolized '
       || 'spontaneous breathing trial (pressure support <=8 cmH2O, 30-120 min) may be considered per unit '
       || 'protocol, terminating on SpO2 <90%, tachypnea, or hemodynamic instability.',
     'Burns et al., AARC Clinical Practice Guideline 2024; Yang & Tobin, N Engl J Med 1991;324:1445-50', 150),

    ('borderline-rsbi-acceptable-oxygenation', 'BORDERLINE', 'NORMAL,AT_RISK,MILD_ARDS', NULL, NULL,
     'A borderline RSBI with acceptable oxygenation supports a closely monitored spontaneous breathing trial; '
       || 'be prepared to abort on tachypnea, desaturation, or hemodynamic instability.',
     'Yang & Tobin, N Engl J Med 1991;324:1445-50; Burns et al., AARC Clinical Practice Guideline 2024', 120);
