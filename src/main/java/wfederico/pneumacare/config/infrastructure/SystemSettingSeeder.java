package wfederico.pneumacare.config.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.config.domain.SettingCategory;
import wfederico.pneumacare.config.infrastructure.persistence.SystemSettingJpaEntity;
import wfederico.pneumacare.config.infrastructure.persistence.SystemSettingRepository;

import java.util.List;

/**
 * Seeds the default system settings on startup in the {@code dev} profile, where
 * Flyway is disabled. Idempotent: each row is inserted only if its key is absent,
 * so existing (possibly edited) values are never overwritten on restart.
 *
 * <p>Staging/prod get the same defaults from {@code V20__create_system_settings.sql}.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class SystemSettingSeeder implements ApplicationRunner {

    private final SystemSettingRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List<SystemSettingJpaEntity> defaults = List.of(
                setting("analytics.window.default.days", "14", SettingCategory.SYSTEM,
                        "Ventana de analítica por defecto (días)",
                        "Rango de fechas inicial para los paneles de analítica.", "number"),
                setting("app.icu.display.name", "UCI Central", SettingCategory.SYSTEM,
                        "Nombre visible de la UCI",
                        "Etiqueta mostrada en el tablero y los reportes.", "text"),
                setting("alert.rsbi.threshold", "105", SettingCategory.CLINICAL_RULES,
                        "Umbral RSBI de alerta",
                        "RSBI por encima de este valor marca destete desfavorable.", "number"),
                setting("alert.pafi.critical", "100", SettingCategory.CLINICAL_RULES,
                        "PaFi crítico",
                        "PaFi por debajo de este valor indica SDRA severo.", "number"),
                setting("ventilator.default.brand", "TECME", SettingCategory.HARDWARE,
                        "Marca de ventilador por defecto",
                        "Marca preseleccionada al abrir el formulario de evaluación.", "text"),
                setting("alert.telegram.enabled", "true", SettingCategory.NOTIFICATIONS,
                        "Alertas por Telegram",
                        "Habilita el envío de alertas clínicas al canal de Telegram.", "boolean"));

        int inserted = 0;
        for (SystemSettingJpaEntity s : defaults) {
            if (!repository.existsById(s.getSettingKey())) {
                repository.save(s);
                inserted++;
            }
        }
        if (inserted > 0) {
            log.info("Seeded {} default system settings (dev profile)", inserted);
        }
    }

    private SystemSettingJpaEntity setting(String key, String value, SettingCategory category,
                                           String label, String description, String valueType) {
        return SystemSettingJpaEntity.builder()
                .settingKey(key)
                .value(value)
                .category(category)
                .label(label)
                .description(description)
                .valueType(valueType)
                .editable(true)
                .build();
    }
}
