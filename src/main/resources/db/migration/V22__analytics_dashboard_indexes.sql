-- =============================================================================
-- V22__analytics_dashboard_indexes.sql
-- PneumaCare - Supporting indexes for the executive analytics aggregation.
--
-- The dashboard counts ventilators by status and alerts by created_at; these
-- indexes keep those aggregations off full table scans as the data grows.
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_physical_ventilators_status ON physical_ventilators (status);
CREATE INDEX IF NOT EXISTS idx_clinical_alerts_log_created_at ON clinical_alerts_log (created_at);
