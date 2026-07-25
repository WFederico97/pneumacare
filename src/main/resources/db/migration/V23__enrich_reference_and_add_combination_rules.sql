-- =============================================================================
-- V23__enrich_reference_and_add_combination_rules.sql
-- PneumaCare - Evidence-grounded, Spanish, bedside-readable clinical consultant.
--
-- Three changes, all non-PII and reference-only:
--   1. Rewrite the single-metric guidance in concise, action-first Spanish so it
--      is fast to scan during a weaning decision.
--   2. Add Berlin-cohort hospital-mortality context to the PaFi/ARDS bands.
--   3. Add a cross-metric knowledge base (clinical_combination_rule): guidance
--      that only applies when several indices co-occur (e.g. a favorable RSBI but
--      ARDS-range oxygenation), which single-metric rows cannot express.
--
-- The consultant renders a weaning verdict headline, then the matched findings as
-- prioritized bullets, then a muted sources line. Deterministic and fully
-- citeable — no hosted or trained model.
-- =============================================================================

-- 1. Rewrite single-metric guidance in Spanish (with ARDS mortality context). --

UPDATE medical_reference SET updated_at = now(), guidance_text =
    'PaFi <100 (SDRA grave, mortalidad ~45%): ventilación protectora, considerar prono y consulta con especialista antes de destetar.'
    WHERE metric = 'PAFI' AND band = 'SEVERE_ARDS';

UPDATE medical_reference SET updated_at = now(), guidance_text =
    'PaFi 100-200 (SDRA moderado, mortalidad ~32%): ventilación protectora, optimizar PEEP y diferir la SBT.'
    WHERE metric = 'PAFI' AND band = 'MODERATE_ARDS';

UPDATE medical_reference SET updated_at = now(), guidance_text =
    'PaFi 200-300 (SDRA leve, mortalidad ~27%): mantener ventilación protectora y vigilar la tendencia de oxigenación.'
    WHERE metric = 'PAFI' AND band = 'MILD_ARDS';

UPDATE medical_reference SET updated_at = now(), guidance_text =
    'PaFi 300-400: por debajo de lo normal; vigilar la oxigenación antes de destetar.'
    WHERE metric = 'PAFI' AND band = 'AT_RISK';

UPDATE medical_reference SET updated_at = now(), guidance_text =
    'RSBI >105: alta probabilidad de fracaso del destete. Diferir la SBT; reevaluar drive, carga y sedación.'
    WHERE metric = 'RSBI' AND band = 'UNFAVORABLE';

UPDATE medical_reference SET updated_at = now(), guidance_text =
    'RSBI 80-105: tolerancia al destete limítrofe; SBT bajo monitoreo estrecho, lista para suspender ante fatiga.'
    WHERE metric = 'RSBI' AND band = 'BORDERLINE';

UPDATE medical_reference SET updated_at = now(), guidance_text =
    'Compliance <50 mL/cmH2O: descartar atelectasia, edema o sobredistensión; reevaluar PEEP y Vt.'
    WHERE metric = 'CSTAT' AND band = 'LOW';

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
     'SDRA grave con compliance baja (mortalidad ~45%): ventilación protectora, considerar prono y consulta con especialista antes de destetar.',
     'ARDS Definition Task Force (Berlin), JAMA 2012;307:2526-33', 210),

    ('high-driving-pressure', NULL, NULL, NULL, 'HIGH',
     'Presión de conducción >15 cmH2O: bajar Vt y/u optimizar PEEP (principal predictor de mortalidad en SDRA).',
     'Amato et al., N Engl J Med 2015;372:747-55', 205),

    ('oxygenation-gates-weaning', 'FAVORABLE', 'MODERATE_ARDS,SEVERE_ARDS', NULL, NULL,
     'RSBI favorable pero PaFi en rango de SDRA: la oxigenación limita el destete. Diferir la SBT y priorizar ventilación protectora.',
     'ARDS Definition Task Force (Berlin), JAMA 2012;307:2526-33; Yang & Tobin, N Engl J Med 1991;324:1445-50', 200),

    ('high-load-stiff-lung', 'UNFAVORABLE', NULL, 'LOW', NULL,
     'RSBI alto + compliance baja (carga alta, pulmón rígido): diferir la SBT; descartar atelectasia/edema y optimizar PEEP y Vt.',
     'Yang & Tobin, N Engl J Med 1991;324:1445-50; Grinnan & Truwit, Crit Care 2005;9:472-84', 180),

    ('weaning-readiness', 'FAVORABLE', 'NORMAL,AT_RISK', 'NORMAL,HIGH', NULL,
     'RSBI, oxigenación y compliance compatibles con destete: considerar SBT protocolizada (PS <=8 cmH2O, 30-120 min); suspender si SpO2 <90%, taquipnea o inestabilidad.',
     'Burns et al., AARC Clinical Practice Guideline 2024; Yang & Tobin, N Engl J Med 1991;324:1445-50', 150),

    ('borderline-rsbi-acceptable-oxygenation', 'BORDERLINE', 'NORMAL,AT_RISK,MILD_ARDS', NULL, NULL,
     'RSBI borderline con oxigenación aceptable: SBT bajo monitoreo estrecho; suspender ante taquipnea, desaturación o inestabilidad.',
     'Yang & Tobin, N Engl J Med 1991;324:1445-50; Burns et al., AARC Clinical Practice Guideline 2024', 120);
