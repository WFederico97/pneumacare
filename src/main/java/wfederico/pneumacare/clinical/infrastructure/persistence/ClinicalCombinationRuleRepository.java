package wfederico.pneumacare.clinical.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for the cross-metric {@code clinical_combination_rule}
 * knowledge base. The table is small (a handful of curated rules), so the
 * consultant loads all rows and evaluates the band allow-lists in memory rather
 * than encoding the wildcard/comma-list matching in SQL.
 */
public interface ClinicalCombinationRuleRepository
        extends JpaRepository<ClinicalCombinationRuleJpaEntity, UUID> {
}
