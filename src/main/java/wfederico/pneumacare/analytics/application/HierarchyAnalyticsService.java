package wfederico.pneumacare.analytics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.analytics.web.dto.HierarchyAnalyticsResponse;
import wfederico.pneumacare.analytics.web.dto.HierarchyAnalyticsResponse.HierarchyRow;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * Multi-level analytics read-model. Aggregates ICU occupancy, active clinical
 * alerts and evaluation volume across the org hierarchy
 * (province → institution → patient) with native SQL, since provinces and
 * hospitals are not modelled as JPA entities.
 *
 * <p>RBAC: the organizational rollups (province, institution) are restricted to
 * director/admin roles; the patient level is available to any clinical role.
 */
@Service
@RequiredArgsConstructor
public class HierarchyAnalyticsService {

    private static final int DEFAULT_WINDOW_DAYS = 14;
    private static final int MIN_WINDOW_DAYS = 1;
    private static final int MAX_WINDOW_DAYS = 365;

    private final JdbcTemplate jdbc;

    private static final String ORG_TEMPLATE = """
            WITH latest_eval AS (
                SELECT DISTINCT ON (e.patient_id) e.patient_id, e.alert_triggered
                FROM evaluations e
                ORDER BY e.patient_id, e.evaluation_time DESC
            ),
            win_eval AS (
                SELECT e.patient_id, COUNT(*) AS cnt
                FROM evaluations e
                WHERE e.evaluation_time >= ?
                GROUP BY e.patient_id
            )
            SELECT %s AS entity_id, %s AS name, %s AS subtitle,
                COUNT(b.id) AS total_beds,
                COUNT(b.id) FILTER (WHERE b.status = 'OCCUPIED') AS occupied,
                COUNT(b.id) FILTER (WHERE b.status = 'AVAILABLE') AS available,
                COUNT(pat.id) FILTER (WHERE le.alert_triggered) AS active_alerts,
                COALESCE(SUM(we.cnt), 0) AS eval_window
            FROM %s
            LEFT JOIN icu_beds b ON b.icu_id = icu.id
            LEFT JOIN patients pat ON pat.bed_id = b.id AND pat.clinical_status = 'ADMITTED'
            LEFT JOIN latest_eval le ON le.patient_id = pat.id
            LEFT JOIN win_eval we ON we.patient_id = pat.id
            GROUP BY %s
            ORDER BY name
            """;

    private static final String PATIENT_SQL = """
            WITH latest_eval AS (
                SELECT DISTINCT ON (e.patient_id) e.patient_id, e.alert_triggered
                FROM evaluations e
                ORDER BY e.patient_id, e.evaluation_time DESC
            ),
            win_eval AS (
                SELECT e.patient_id, COUNT(*) AS cnt
                FROM evaluations e
                WHERE e.evaluation_time >= ?
                GROUP BY e.patient_id
            )
            SELECT pat.id::text AS entity_id, b.bed_number AS name, icu.name AS subtitle,
                1 AS total_beds, 1 AS occupied, 0 AS available,
                CASE WHEN le.alert_triggered THEN 1 ELSE 0 END AS active_alerts,
                COALESCE(we.cnt, 0) AS eval_window
            FROM patients pat
            JOIN icu_beds b ON b.id = pat.bed_id
            JOIN intensive_care_units icu ON icu.id = b.icu_id
            LEFT JOIN latest_eval le ON le.patient_id = pat.id
            LEFT JOIN win_eval we ON we.patient_id = pat.id
            WHERE pat.clinical_status = 'ADMITTED'
            ORDER BY name
            """;

    private static final RowMapper<HierarchyRow> ROW_MAPPER = (rs, i) -> {
        long total = rs.getLong("total_beds");
        long occupied = rs.getLong("occupied");
        int rate = total == 0 ? 0 : (int) Math.round(occupied * 100.0 / total);
        return new HierarchyRow(
                rs.getString("entity_id"),
                rs.getString("name"),
                rs.getString("subtitle"),
                total,
                occupied,
                rs.getLong("available"),
                rate,
                rs.getLong("active_alerts"),
                rs.getLong("eval_window"));
    };

    @Transactional(readOnly = true)
    public HierarchyAnalyticsResponse aggregate(HierarchyLevel level, int windowDays, Set<String> roles) {
        boolean orgLevel = level == HierarchyLevel.PROVINCE || level == HierarchyLevel.INSTITUTION;
        if (orgLevel && !roles.contains("ROLE_DIRECTOR") && !roles.contains("ROLE_ADMIN")) {
            throw new BusinessLayerException(
                    "No autorizado para ver la agregación por " + level, HttpStatus.FORBIDDEN);
        }

        int window = Math.max(MIN_WINDOW_DAYS, Math.min(MAX_WINDOW_DAYS, windowDays));
        Timestamp since = Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(window).toInstant());

        List<HierarchyRow> rows = switch (level) {
            case PROVINCE -> jdbc.query(provinceSql(), ROW_MAPPER, since);
            case INSTITUTION -> jdbc.query(institutionSql(), ROW_MAPPER, since);
            case PATIENT -> jdbc.query(PATIENT_SQL, ROW_MAPPER, since);
        };
        return new HierarchyAnalyticsResponse(level, window, rows);
    }

    private String provinceSql() {
        return ORG_TEMPLATE.formatted(
                "p.id::text", "p.name", "p.region",
                """
                provinces p
                LEFT JOIN hospitals h ON h.province_id = p.id
                LEFT JOIN intensive_care_units icu ON icu.hospital_id = h.id
                """,
                "p.id, p.name, p.region");
    }

    private String institutionSql() {
        return ORG_TEMPLATE.formatted(
                "h.id::text", "h.name", "p.name",
                """
                hospitals h
                JOIN provinces p ON p.id = h.province_id
                LEFT JOIN intensive_care_units icu ON icu.hospital_id = h.id
                """,
                "h.id, h.name, p.name");
    }
}
