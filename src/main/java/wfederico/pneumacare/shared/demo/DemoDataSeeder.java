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
        for (int i = 0; i < patients.size(); i++) {
            UUID patientId = seedPatient(patients.get(i), icuId, ctx.bedIds().get(i), dniType, i);
            seedEvaluations(patients.get(i), patientId, ctx);
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
