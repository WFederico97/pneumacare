package wfederico.pneumacare.shared.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.clinical.application.strategy.VentilatorFactory;
import wfederico.pneumacare.clinical.domain.MetricBreach;
import wfederico.pneumacare.clinical.domain.RiskThresholdEvaluator;
import wfederico.pneumacare.clinical.domain.VentilatorBrand;
import wfederico.pneumacare.clinical.domain.input.VentilatorReading;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityRepository;
import wfederico.pneumacare.shared.security.bootstrap.BootstrapAdminProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds a curated demo dataset (6 patients + evaluations in a dedicated Demo ICU)
 * on startup, once, for demonstration builds.
 *
 * <p>Active only when {@code app.demo.seed.enabled=true} (set in {@code .env.prod}),
 * so it never runs in dev/test/CI. Idempotent: a no-op once the Demo ICU exists.
 * Ordered after {@code BootstrapAdminSeeder} (LOWEST_PRECEDENCE) so the
 * {@code admin} user exists to reference as chief/creator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(DemoDataSeeder.ORDER)
@EnableConfigurationProperties(DemoDataProperties.class)
@ConditionalOnProperty(prefix = "app.demo.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    static final int ORDER = Ordered.LOWEST_PRECEDENCE; // after admin seeder (Task 6)

    /** Deterministic so app.security.default-icu-id can point at it (Task 8). */
    static final String DEMO_ICU_ID = "eeeeeeee-0000-0000-0000-000000000001";
    static final String DEMO_ICU_CODE = "DEMO-ICU";

    private static final int SNAPSHOT_SCALE = 2;

    private final JdbcClient jdbcClient;
    private final BootstrapAdminProperties adminProperties;
    private final PatientIdentityRepository patientIdentityRepository;
    private final PatientIdentifierTypeRepository identifierTypeRepository;
    private final VentilatorFactory ventilatorFactory;

    /** Ids created during seeding, threaded between steps. */
    private record DemoContext(UUID shiftId, UUID ventilatorId, UUID adminUserId, List<UUID> bedIds) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoAlreadySeeded()) {
            log.info("Demo data already present (ICU '{}'); skipping seed.", DEMO_ICU_CODE);
            return;
        }
        log.info("Seeding demo dataset (Demo ICU + 6 patients)...");
        DemoContext ctx = seedInfrastructure();
        seedPatientsAndEvaluations(ctx);
        removeStrayBeds();
        log.info("Demo dataset seeded.");
    }

    private boolean demoAlreadySeeded() {
        Integer count = jdbcClient.sql(
                "SELECT count(*) FROM intensive_care_units WHERE code = :code")
                .param("code", DEMO_ICU_CODE)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private DemoContext seedInfrastructure() {
        UUID adminUserId = jdbcClient.sql(
                "SELECT id FROM users WHERE username = :u")
                .param("u", adminProperties.getUsername())
                .query(UUID.class)
                .single();

        UUID provinceId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO provinces (id, name, region) VALUES (:id, 'Demo', 'Demo')")
                .param("id", provinceId).update();

        UUID hospitalId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO hospitals (id, province_id, name, institutional_type_id)
                VALUES (:id, :prov, 'Hospital Demo', (SELECT MIN(id) FROM institutional_types))
                """)
                .param("id", hospitalId).param("prov", provinceId).update();

        UUID icuId = UUID.fromString(DEMO_ICU_ID);
        jdbcClient.sql("""
                INSERT INTO intensive_care_units (id, hospital_id, name, code)
                VALUES (:id, :hosp, 'Demo ICU', :code)
                """)
                .param("id", icuId).param("hosp", hospitalId).param("code", DEMO_ICU_CODE).update();

        List<UUID> bedIds = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            UUID bedId = UUID.randomUUID();
            bedIds.add(bedId);
            jdbcClient.sql("""
                    INSERT INTO icu_beds (id, icu_id, bed_number, status)
                    VALUES (:id, :icu, :num, 'OCCUPIED')
                    """)
                    .param("id", bedId).param("icu", icuId)
                    .param("num", String.format("DEMO-%02d", i)).update();
        }

        UUID modelId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO ventilator_models (id, brand, model, software_version)
                VALUES (:id, 'TECME', 'Tourus Demo', '1.0')
                """)
                .param("id", modelId).update();

        UUID ventilatorId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO physical_ventilators (id, icu_id, model_id, serial_number, status)
                VALUES (:id, :icu, :model, 'DEMO-VENT-001', 'AVAILABLE')
                """)
                .param("id", ventilatorId).param("icu", icuId).param("model", modelId).update();
        // second ventilator for realism
        jdbcClient.sql("""
                INSERT INTO physical_ventilators (id, icu_id, model_id, serial_number, status)
                VALUES (:id, :icu, :model, 'DEMO-VENT-002', 'AVAILABLE')
                """)
                .param("id", UUID.randomUUID()).param("icu", icuId).param("model", modelId).update();

        UUID shiftId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO medical_shifts (id, icu_id, chief_user_id, start_time, end_time, status)
                VALUES (:id, :icu, :chief, :start, NULL, 'OPEN')
                """)
                .param("id", shiftId).param("icu", icuId).param("chief", adminUserId)
                .param("start", OffsetDateTime.now().minusDays(3)).update();

        return new DemoContext(shiftId, ventilatorId, adminUserId, bedIds);
    }

    private void seedPatientsAndEvaluations(DemoContext ctx) {
        UUID icuId = UUID.fromString(DEMO_ICU_ID);
        PatientIdentifierTypeJpaEntity dniType = resolveDniType();
        List<DemoScenarios.Patient> patients = DemoScenarios.patients();
        List<UUID> patientIds = new ArrayList<>();
        for (int i = 0; i < patients.size(); i++) {
            UUID patientId = seedPatient(patients.get(i), icuId, ctx.bedIds().get(i), dniType, i);
            seedEvaluations(patients.get(i), patientId, ctx);
            patientIds.add(patientId);
        }
        seedAirwayEvents(ctx, patientIds);
        seedWeaningTrials(ctx, patientIds);
        seedClosedEpisodes(ctx, dniType);
    }

    /**
     * Closed backdated episodes so the executive metrics (true ALOS, turnover,
     * mortality, readmission) are non-zero on the demo: 2 HOME, 1 WARD,
     * 1 TRANSFER_EXTERNAL, 1 DECEASED (weaning failure), 1 readmission pair.
     */
    private void seedClosedEpisodes(DemoContext ctx, PatientIdentifierTypeJpaEntity dniType) {
        UUID icuId = UUID.fromString(DEMO_ICU_ID);
        OffsetDateTime now = OffsetDateTime.now();

        record ClosedEpisode(String first, String last, String disposition, int admitted, int discharged) {}
        List<ClosedEpisode> episodes = List.of(
                new ClosedEpisode("Hugo", "Alvarez", "HOME", 20, 14),
                new ClosedEpisode("Nora", "Benitez", "HOME", 18, 12),
                new ClosedEpisode("Ivan", "Castro", "WARD", 16, 9),
                new ClosedEpisode("Rita", "Dominguez", "TRANSFER_EXTERNAL", 15, 10),
                new ClosedEpisode("Oscar", "Esposito", "DECEASED", 21, 14));

        List<UUID> closedIds = new ArrayList<>();
        for (int i = 0; i < episodes.size(); i++) {
            ClosedEpisode e = episodes.get(i);
            UUID identityId = seedClosedIdentity(e.first(), e.last(), dniType, 100 + i);
            closedIds.add(insertClosedEpisode(icuId, identityId, e.disposition(),
                    now.minusDays(e.admitted()), now.minusDays(e.discharged())));
        }

        seedWeaningFailureHistory(ctx, closedIds.get(4), now);
        seedReadmissionPair(ctx, icuId, dniType, now);
    }

    /** PII identity + DNI for a closed-episode demo patient (index offset avoids DNI collisions). */
    private UUID seedClosedIdentity(String first, String last,
                                    PatientIdentifierTypeJpaEntity dniType, int index) {
        PatientIdentityJpaEntity identity = PatientIdentityJpaEntity.builder()
                .firstName(first)
                .lastName(last)
                .birthDate(java.time.LocalDate.of(1960, 1, 1).plusYears(index % 30))
                .build();
        PatientIdentifierJpaEntity dni = PatientIdentifierJpaEntity.builder()
                .patientIdentifierName(String.format("%08d", 40_000_000 + index * 101_010))
                .patientIdentity(identity)
                .patientIdentifierType(dniType)
                .build();
        identity.addIdentifier(dni);
        return patientIdentityRepository.saveAndFlush(identity).getId();
    }

    /** Closed episode row: no bed, terminus set, status derived from the disposition. */
    private UUID insertClosedEpisode(UUID icuId, UUID identityId, String disposition,
                                     OffsetDateTime admitted, OffsetDateTime discharged) {
        String status = "TRANSFER_EXTERNAL".equals(disposition) ? "TRANSFERRED" : "DISCHARGED";
        UUID patientId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO patients
                    (id, icu_id, identity_id, bed_id, clinical_status, respiratory_status,
                     admission_date, discharge_date, disposition)
                VALUES
                    (:id, :icu, :identity, NULL, :status, 'SPONTANEOUS', :admitted, :discharged, :disposition)
                """)
                .param("id", patientId)
                .param("icu", icuId)
                .param("identity", identityId)
                .param("status", status)
                .param("admitted", admitted)
                .param("discharged", discharged)
                .param("disposition", disposition)
                .update();
        return patientId;
    }

    /**
     * Weaning-failure history for the deceased episode: two failed SBTs, an
     * extubation and a reintubation 30 h later (inside the 48 h failure window).
     */
    private void seedWeaningFailureHistory(DemoContext ctx, UUID patientId, OffsetDateTime now) {
        OffsetDateTime intubated = now.minusDays(21);
        OffsetDateTime extubated = now.minusDays(17);
        OffsetDateTime reintubated = extubated.plusHours(30);
        insertAirwayEvent(ctx, patientId, "INTUBATION", intubated);
        insertAirwayEvent(ctx, patientId, "EXTUBATION", extubated);
        insertAirwayEvent(ctx, patientId, "INTUBATION", reintubated);
        for (int a = 0; a < 2; a++) {
            jdbcClient.sql("""
                    INSERT INTO spontaneous_breathing_trials
                        (id, patient_id, shift_id, duration_minutes, outcome, created_by, created_at)
                    VALUES (:id, :patient, :shift, 30, 'FAILURE', :createdBy, :recordedAt)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("patient", patientId)
                    .param("shift", ctx.shiftId())
                    .param("createdBy", ctx.adminUserId())
                    .param("recordedAt", now.minusDays(18).plusHours(6L * a))
                    .update();
        }
    }

    private void insertAirwayEvent(DemoContext ctx, UUID patientId, String type, OffsetDateTime time) {
        jdbcClient.sql("""
                INSERT INTO airway_events (id, patient_id, shift_id, event_time, event_type, created_by)
                VALUES (:id, :patient, :shift, :time, :type, :createdBy)
                """)
                .param("id", UUID.randomUUID())
                .param("patient", patientId)
                .param("shift", ctx.shiftId())
                .param("time", time)
                .param("type", type)
                .param("createdBy", ctx.adminUserId())
                .update();
    }

    /** Same identity, second episode opening 3 days after the first closes (7-day readmission). */
    private void seedReadmissionPair(DemoContext ctx, UUID icuId,
                                     PatientIdentifierTypeJpaEntity dniType, OffsetDateTime now) {
        UUID identityId = seedClosedIdentity("Sofia", "Ferrari", dniType, 110);
        insertClosedEpisode(icuId, identityId, "WARD", now.minusDays(12), now.minusDays(6));
        jdbcClient.sql("""
                INSERT INTO patients
                    (id, icu_id, identity_id, bed_id, clinical_status, respiratory_status, admission_date)
                VALUES (:id, :icu, :identity, NULL, 'ADMITTED', 'SPONTANEOUS', :admitted)
                """)
                .param("id", UUID.randomUUID())
                .param("icu", icuId)
                .param("identity", identityId)
                .param("admitted", now.minusDays(3))
                .update();
    }

    /**
     * Emits one INTUBATION airway event per demo patient at admission time so the
     * event log is consistent with the seeded {@code INTUBATED} status. Without
     * this the ventilator-days analytic (folded from the event log) reads zero
     * despite the intubation census being non-zero.
     */
    private void seedAirwayEvents(DemoContext ctx, List<UUID> patientIds) {
        OffsetDateTime intubatedAt = OffsetDateTime.now().minusDays(3);
        for (UUID patientId : patientIds) {
            jdbcClient.sql("""
                    INSERT INTO airway_events (id, patient_id, shift_id, event_time, event_type, created_by)
                    VALUES (:id, :patient, :shift, :time, 'INTUBATION', :createdBy)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("patient", patientId)
                    .param("shift", ctx.shiftId())
                    .param("time", intubatedAt)
                    .param("createdBy", ctx.adminUserId())
                    .update();
        }
    }

    /**
     * Seeds a spread of SBT attempt counts so the WIND weaning classification
     * shows a realistic distribution: two Simple (1 attempt), two Difficult
     * (2–3 attempts), one Prolonged (4 attempts) and one with no attempt yet.
     */
    private void seedWeaningTrials(DemoContext ctx, List<UUID> patientIds) {
        // Failure count preceding the final SUCCESS, per patient index. An empty
        // array leaves that patient with no SBT (WIND "Sin intento").
        int[][] plan = { {1}, {1}, {0, 1}, {0, 0, 1}, {0, 0, 0, 1}, {} };
        for (int i = 0; i < patientIds.size() && i < plan.length; i++) {
            int[] outcomes = plan[i];
            for (int a = 0; a < outcomes.length; a++) {
                boolean success = outcomes[a] == 1;
                jdbcClient.sql("""
                        INSERT INTO spontaneous_breathing_trials
                            (id, patient_id, shift_id, duration_minutes, outcome, created_by, created_at)
                        VALUES (:id, :patient, :shift, :dur, :outcome, :createdBy, :recordedAt)
                        """)
                        .param("id", UUID.randomUUID())
                        .param("patient", patientIds.get(i))
                        .param("shift", ctx.shiftId())
                        .param("dur", success ? 120 : 30)
                        .param("outcome", success ? "SUCCESS" : "FAILURE")
                        .param("createdBy", ctx.adminUserId())
                        .param("recordedAt", OffsetDateTime.now().minusHours(12L * (outcomes.length - a)))
                        .update();
            }
        }
    }

    /** Loads the DNI identifier type (seeded by Flyway V4) as a managed entity. */
    private PatientIdentifierTypeJpaEntity resolveDniType() {
        Integer dniTypeId = jdbcClient.sql(
                "SELECT patient_identifier_type_id FROM patient_identifier_types WHERE patient_identifier_type_name = 'DNI'")
                .query(Integer.class)
                .single();
        return identifierTypeRepository.findById(dniTypeId)
                .orElseThrow(() -> new IllegalStateException("DNI identifier type not seeded (Flyway V4)."));
    }

    /**
     * Creates the encrypted PII identity (name + one DNI identifier) via JPA so
     * AesAttributeConverter runs, then the operational patient row via JDBC,
     * assigned to the given bed. Real admissions always carry an identifier, so
     * seeding one keeps the demo data consistent with what the UI expects.
     *
     * @return the new patients.id
     */
    private UUID seedPatient(DemoScenarios.Patient p, UUID icuId, UUID bedId,
                             PatientIdentifierTypeJpaEntity dniType, int index) {
        PatientIdentityJpaEntity identity = PatientIdentityJpaEntity.builder()
                .firstName(p.firstName())
                .lastName(p.lastName())
                .birthDate(p.birthDate())
                .build();
        PatientIdentifierJpaEntity dni = PatientIdentifierJpaEntity.builder()
                .patientIdentifierName(String.format("%08d", 30_000_000 + index * 1_111_111))
                .patientIdentity(identity)
                .patientIdentifierType(dniType)
                .build();
        identity.addIdentifier(dni);
        UUID identityId = patientIdentityRepository.saveAndFlush(identity).getId();

        UUID patientId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO patients
                    (id, icu_id, identity_id, bed_id, clinical_status, respiratory_status, admission_date)
                VALUES
                    (:id, :icu, :identity, :bed, 'ADMITTED', 'INTUBATED', :admitted)
                """)
                .param("id", patientId)
                .param("icu", icuId)
                .param("identity", identityId)
                .param("bed", bedId)
                .param("admitted", OffsetDateTime.now().minusDays(3))
                .update();
        return patientId;
    }

    private void seedEvaluations(DemoScenarios.Patient p, UUID patientId, DemoContext ctx) {
        OffsetDateTime seedNow = OffsetDateTime.now();
        for (DemoScenarios.Reading r : p.readings()) {
            VentilatorReading reading = new VentilatorReading(
                    r.f(), r.vt(), r.pao2(), r.fio2(), r.pplat(), r.peep());
            VentilatorEvaluationResult result =
                    ventilatorFactory.resolve(VentilatorBrand.TECME).evaluate(reading);

            List<MetricBreach> breaches = RiskThresholdEvaluator.evaluate(
                    result.rsbi().value(), result.pafi().value(), result.cstat().value());

            jdbcClient.sql("""
                    INSERT INTO evaluations
                        (id, patient_id, shift_id, physical_ventilator_id, evaluation_time,
                         f, vt, pao2, fio2, pplat, peep,
                         rsbi_snapshot, pafi_snapshot, cstat_snapshot,
                         rsbi_interpretation, pafi_classification, cstat_interpretation,
                         alert_triggered, created_by)
                    VALUES
                        (:id, :patient, :shift, :vent, :time,
                         :f, :vt, :pao2, :fio2, :pplat, :peep,
                         :rsbi, :pafi, :cstat,
                         :rsbiI, :pafiI, :cstatI,
                         :alert, :createdBy)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("patient", patientId)
                    .param("shift", ctx.shiftId())
                    .param("vent", ctx.ventilatorId())
                    .param("time", seedNow.plusDays(r.dayOffset()))
                    .param("f", bd(r.f())).param("vt", bd(r.vt()))
                    .param("pao2", bd(r.pao2())).param("fio2", bd(r.fio2()))
                    .param("pplat", bd(r.pplat())).param("peep", bd(r.peep()))
                    .param("rsbi", bd(result.rsbi().value()))
                    .param("pafi", bd(result.pafi().value()))
                    .param("cstat", bd(result.cstat().value()))
                    .param("rsbiI", result.rsbi().interpretation().name())
                    .param("pafiI", result.pafi().classification().name())
                    .param("cstatI", result.cstat().interpretation().name())
                    .param("alert", !breaches.isEmpty())
                    .param("createdBy", ctx.adminUserId())
                    .update();
        }
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(SNAPSHOT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Removes beds outside the Demo ICU (e.g. the V26 default-ICU beds recreated
     * by Flyway) so the hospital-wide occupancy summary counts only the demo —
     * keeping it consistent with the ICU-scoped bed grid. Those beds are unused
     * once the session is pointed at the Demo ICU (DEFAULT_ICU_ID).
     */
    private void removeStrayBeds() {
        int removed = jdbcClient.sql("DELETE FROM icu_beds WHERE icu_id <> :demo")
                .param("demo", UUID.fromString(DEMO_ICU_ID))
                .update();
        if (removed > 0) {
            log.info("Removed {} non-demo bed(s) so occupancy reflects the Demo ICU only.", removed);
        }
    }
}
