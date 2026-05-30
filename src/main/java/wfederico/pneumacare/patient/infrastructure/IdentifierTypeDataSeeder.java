package wfederico.pneumacare.patient.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeRepository;

import java.util.List;

/**
 * Seeds the {@code patient_identifier_types} catalog table on startup in the
 * {@code dev} profile.
 *
 * <p>In staging and production Flyway migration {@code V4__seed_identifier_types.sql}
 * handles seeding — this bean is not active there. In the test context (which
 * defaults to the {@code dev} profile) this runner fires at context startup,
 * so integration tests find the catalog pre-populated without needing their
 * own {@code @BeforeEach} setup.
 *
 * <p>The check {@code repository.count() > 0} makes the runner idempotent:
 * re-starting the application against an already-seeded dev database is safe.
 */
@Slf4j
@Profile("dev")
@Component
@RequiredArgsConstructor
public class IdentifierTypeDataSeeder implements ApplicationRunner {

    private final PatientIdentifierTypeRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.debug("IdentifierTypeDataSeeder: catalog already present, skipping.");
            return;
        }

        log.info("IdentifierTypeDataSeeder: seeding identifier type catalog.");
        repository.saveAll(List.of(
                build("DNI",       "Documento Nacional de Identidad"),
                build("CUIL",      "Código Único de Identificación Laboral"),
                build("CUIT",      "Código Único de Identificación Tributaria"),
                build("LE",        "Libreta de Enrolamiento"),
                build("LC",        "Libreta Cívica"),
                build("Pasaporte", "Pasaporte")
        ));
        log.info("IdentifierTypeDataSeeder: 6 identifier types seeded.");
    }

    private PatientIdentifierTypeJpaEntity build(String name, String description) {
        return PatientIdentifierTypeJpaEntity.builder()
                .patientIdentifierTypeName(name)
                .patientIdentifierTypeDescription(description)
                .build();
    }
}
