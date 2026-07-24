# PneumaCare Analytics Catalog

**Updated:** 2026-07-24

Single source of truth for every dashboard metric: clinical definition, formula,
data sources, recommended visualization, target role, and build status.

**Stack note.** PneumaCare is **Spring Boot 4 / Java 17 / PostgreSQL 17** (not
.NET / SQL Server, as an earlier analyst brief assumed). Aggregations run as
JPQL/SQL count-and-average queries at request time, supported by targeted
indexes (`V22`, `V29`). The SQL Server "indexed view / stored procedure"
strategy maps to **PostgreSQL materialized views** with scheduled refresh —
deliberately deferred until data volume demands it (see [Proposed](#proposed)).

**Status legend:**
- `live` — implemented and served today
- `building` — this iteration (episode-terminus work, spec of 2026-07-24)
- `proposed` — designed here, not yet scheduled

---

## 1. Clinical / sanitary scope

Target roles: respiratory therapists, chief of guard. Goal: anticipation, daily
operational control, therapeutic success at the bedside.
Served by `GET /api/v1/analytics/summary` (role-scoped) and `GET /api/v1/alerts`.

### 1.1 Extubation / weaning success rate — `live`

- **Definition:** of extubations recorded in the window, the fraction *not*
  followed by re-intubation of the same patient within 48 h. A re-intubation
  ≤ 48 h is an extubation failure (standard ICU quality definition).
- **Formula:** `(extubations − reintubations48h) / extubations × 100`
- **Variables:** `airway_events(patient_id, event_type, event_time)` — intervals
  folded in `AnalyticsService`; one pass feeds this and ventilator-days.
- **UI:** gauge (0–100 %) with a 85–90 % reference band; count pair beneath.
- **DTO:** `AnalyticsSummaryResponse.ExtubationStats`

### 1.2 SBT mapping — `live` (counts) / `proposed` (failure reasons)

- **Definition:** spontaneous breathing trials tolerated vs. failed in the window.
- **Formula:** counts by `sbt.result`.
- **Variables:** `sbt(patient_id, result, recorded_at)` (V12 recorded-result columns).
- **Gap:** failure *reasons* (tachypnea, desaturation, agitation, hemodynamic
  instability) are not captured — needs an enum column on `sbt` before the
  reason breakdown can exist.
- **UI:** stacked bar per day; failure-reason donut once reasons exist.
- **DTO:** `AnalyticsSummaryResponse.WeaningStats`

### 1.3 RSBI readiness distribution — `live`

- **Definition:** currently-ventilated patients bucketed by latest RSBI
  interpretation: favorable (< 105), borderline, unfavorable (Yang–Tobin).
- **Variables:** `evaluations(rsbi, rsbi_interpretation, evaluation_time)`.
- **UI:** three-segment horizontal bar (traffic-light).
- **DTO:** `WeaningStats.rsbiFavorable/Borderline/Unfavorable`

### 1.4 WIND weaning classification — `live`

- **Definition:** WIND-aligned difficulty across currently-ventilated patients,
  approximated from SBT attempt counts (Béduneau et al. 2017): no attempt,
  simple (1), difficult (2–3), prolonged (> 3).
- **UI:** 4-column distribution bar; prolonged-weaning count as a callout.
- **DTO:** `AnalyticsSummaryResponse.WeaningClassificationStats`

### 1.5 Lung-protection surveillance (driving pressure) — `live`

- **Definition:** patients whose latest evaluation carries ΔP > 15 cmH₂O — the
  mortality-associated threshold (Amato 2015).
- **Variables:** `evaluations` driving-pressure band (`DrivingPressureBand`).
- **UI:** stat tile, red when > 0; click-through to the bed list.
- **DTO:** `AnalyticsSummaryResponse.LungProtectionStats`

### 1.6 IMV load: currently intubated + ventilator-days — `live`

- **Definition:** patients presently INTUBATED, plus endotracheal patient-days
  falling within the window (intervals folded from the airway-event log; open
  intubations counted to now).
- **UI:** stat tile pair; ventilator-days as a 7-day sparkline.
- **DTO:** `AnalyticsSummaryResponse.VentilationStats`

### 1.7 Active clinical alerts — `live`

- **Definition:** admitted patients whose most recent evaluation tripped a
  threshold (RSBI / PaFi / Cstat), identified by bed and ICU (no PII), with the
  triggering snapshots for at-a-glance triage.
- **Endpoint:** `GET /api/v1/alerts`
- **UI:** triage table sorted by severity; row color by worst metric.
- **DTO:** `ActiveAlertResponse`

### 1.8 Evaluation activity trend — `live`

- **Definition:** evaluations recorded per day (count series). *Not* a
  physiological trend — see 1.9.
- **UI:** sparkline / small bar series.
- **DTO:** `AnalyticsSummaryResponse.TrendPoint[]`

### 1.9 Per-patient longitudinal physiological trends — `proposed`

- **Definition:** RSBI, PaFi, and static compliance for one patient over the
  last 5 days — the therapist's trajectory view ("is this patient converging
  toward weanable?").
- **Why it matters:** every value already exists in `evaluations`; the missing
  piece is only a per-patient series endpoint
  (`GET /api/v1/analytics/patients/{id}/trends?days=5`). Highest-value purely
  additive clinical feature.
- **UI:** three aligned sparklines with threshold reference lines
  (RSBI 105, PaFi 300/200, Cstat 50) on the bed-grid patient drawer.

### 1.10 Alert frequency per shift — `proposed`

- **Definition:** alert count bucketed by shift (from `shift_handovers`
  intervals) rather than the current flat 7-day total.
- **Why:** "which shift generates/receives the alert load" is the chief of
  guard's staffing question; a flat total cannot answer it.
- **Variables:** `clinical_alerts_log.created_at` × shift intervals.
- **UI:** heatmap (day × shift).

---

## 2. Executive scope

Target roles: hospital director, Ministry of Health. Goal: resource management,
institutional quality, cost. Served by `GET /api/v1/analytics/dashboard`
(role-guarded, `ROLE_DIRECTOR`/admin) and `GET /api/v1/analytics/hierarchy`.

### 2.1 ICU bed occupancy — `live`

- **Definition:** occupied ÷ total beds, real-time.
- **Variables:** `icu_beds.status`.
- **UI:** gauge with 85 % saturation warning band.
- **DTO:** `ExecutiveDashboardResponse.occupancyRatePercent`

### 2.2 Hierarchy drill-down (province → institution → bed) — `live`

- **Definition:** occupancy, active alerts, and evaluation volume rolled up by
  province, institution, or patient/bed level; org levels director/admin only.
- **UI:** drill-down table or treemap; occupancy as inline bar per row.
- **DTO:** `HierarchyAnalyticsResponse`

### 2.3 Asset utilization matrix — `live`

- **Definition:** ventilator fleet by status — in-use / available / maintenance —
  plus utilization = inUse ÷ fleet.
- **Variables:** `physical_ventilators.status`, `asset_assignments` (active
  lookup via `GET /api/v1/assets/active`).
- **UI:** status matrix + utilization gauge; maintenance count as amber tile.
- **DTO:** `ExecutiveDashboardResponse.AssetUtilization`

### 2.4 Alert frequency (7-day) — `live`

- **Definition:** clinical alerts logged in the last 7 days (flat total).
- **DTO:** `ExecutiveDashboardResponse.alertFrequencyLast7Days`
- **Upgrade path:** per-shift heatmap (1.10) and SLA (2.9).

### 2.5 True ALOS — `building`

- **Definition:** mean `discharge_date − admission_date` over episodes *closed
  in the window*. Replaces the current proxy (mean stay of currently-admitted
  patients), which survives as `currentCensusMeanStayDays`.
- **Variables:** `patients(admission_date, discharge_date)` — V29 terminus.
- **UI:** stat tile with 30-day trend sparkline; census-mean tile beside it.

### 2.6 Bed turnover — `building`

- **Definition:** episodes closed in window ÷ total beds — throughput per bed.
- **UI:** stat tile; monthly bar series once history accrues.

### 2.7 ICU mortality & weaning-failure mortality — `building`

- **Definition:** (`DECEASED` + `WITHDRAWAL_OF_CARE`) ÷ closed episodes, with
  withdrawal reported separately (conflating them distorts risk-adjusted
  mortality). Weaning-failure mortality: of episodes with ≥ 1 failed SBT or a
  48 h reintubation, the fraction ending `DECEASED` — the M&M linkage the
  original brief asked for.
- **Variables:** `patients.disposition` × SBT results × airway-event folds.
- **UI:** two stat tiles; weaning-failure cohort size shown as denominator.

### 2.8 Readmission rate (48 h / 7 d) — `building`

- **Definition:** episodes where the same `identity_id` is readmitted within
  N of the prior `discharge_date`. Standard discharge-quality indicator; early
  readmission suggests premature discharge.
- **Variables:** episode pairs on `patients(identity_id, admission_date,
  discharge_date)` — enabled by dropping `uq_patients_identity` (V29).
- **UI:** stat tile pair with target thresholds.

### 2.9 Clinical SLA — alert response time — `proposed`

- **Definition:** median/p90 time from alert dispatch to acknowledgement.
- **Blocked by:** one column — `clinical_alerts_log.acknowledged_at` — plus an
  ack endpoint. Not by any missing service.
- **UI:** p50/p90 stat tiles + histogram.

### 2.10 Occupancy history — `proposed`

- **Definition:** daily occupancy snapshots for historical trend (current
  metric is point-in-time only). Needs a nightly snapshot row or an
  episode-interval fold.
- **UI:** area chart with saturation band.

---

## Proposed

Aggregation strategy at scale: when closed-episode volume makes request-time
aggregation slow (thousands of episodes), promote 2.5–2.8 to a PostgreSQL
**materialized view** (`mv_episode_stats`) refreshed by a scheduled job
(`REFRESH MATERIALIZED VIEW CONCURRENTLY`). The DTO contract does not change —
only the repository's source relation. This is the PostgreSQL equivalent of the
SQL Server indexed-view plan in the original brief.
