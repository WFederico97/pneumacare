-- =============================================================================
-- V27__drop_unused_ai_clinical_insights.sql
-- Drops the ai_clinical_insights table created in V1.
--
-- It was a placeholder for an LLM-based insight cache that was never built:
-- no JPA entity maps it and no code reads or writes it. The deterministic
-- clinical consultant caches its guidance in clinical_consultant_insights (V20)
-- instead. Removing the dead table keeps the schema honest under ddl-auto=validate.
-- =============================================================================

DROP TABLE IF EXISTS ai_clinical_insights;
