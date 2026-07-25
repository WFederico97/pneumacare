package wfederico.pneumacare.analytics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.analytics.web.dto.ActiveAlertResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read-model for the Alertas view: lists admitted patients whose most recent
 * evaluation tripped a clinical threshold ({@code alert_triggered}), enriched
 * with the triggering metric snapshots. Native SQL because it spans beds,
 * patients and evaluations, which are not modelled as a single JPA aggregate.
 */
@Service
@RequiredArgsConstructor
public class ActiveAlertsService {

    private final JdbcTemplate jdbc;

    private static final String SQL = """
            WITH latest_eval AS (
                SELECT DISTINCT ON (e.patient_id)
                    e.patient_id, e.evaluation_time, e.alert_triggered,
                    e.rsbi_snapshot, e.rsbi_interpretation,
                    e.pafi_snapshot, e.pafi_classification,
                    e.cstat_snapshot, e.cstat_interpretation
                FROM evaluations e
                ORDER BY e.patient_id, e.evaluation_time DESC
            )
            SELECT pat.id::text AS patient_id, b.bed_number, icu.name AS icu_name,
                le.evaluation_time,
                le.rsbi_snapshot, le.rsbi_interpretation,
                le.pafi_snapshot, le.pafi_classification,
                le.cstat_snapshot, le.cstat_interpretation
            FROM patients pat
            JOIN icu_beds b ON b.id = pat.bed_id
            JOIN intensive_care_units icu ON icu.id = b.icu_id
            JOIN latest_eval le ON le.patient_id = pat.id
            WHERE pat.clinical_status = 'ADMITTED' AND le.alert_triggered = true
            ORDER BY le.evaluation_time DESC
            """;

    private static final RowMapper<ActiveAlertResponse> MAPPER = ActiveAlertsService::mapRow;

    @Transactional(readOnly = true)
    public List<ActiveAlertResponse> activeAlerts() {
        return jdbc.query(SQL, MAPPER);
    }

    private static ActiveAlertResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime evaluationTime = rs.getObject("evaluation_time", OffsetDateTime.class);
        return new ActiveAlertResponse(
                rs.getString("patient_id"),
                rs.getString("bed_number"),
                rs.getString("icu_name"),
                evaluationTime,
                getNullableDouble(rs, "rsbi_snapshot"),
                rs.getString("rsbi_interpretation"),
                getNullableDouble(rs, "pafi_snapshot"),
                rs.getString("pafi_classification"),
                getNullableDouble(rs, "cstat_snapshot"),
                rs.getString("cstat_interpretation"));
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
