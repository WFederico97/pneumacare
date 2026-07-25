package wfederico.pneumacare.config.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.config.infrastructure.persistence.SystemSettingJpaEntity;
import wfederico.pneumacare.config.infrastructure.persistence.SystemSettingRepository;
import wfederico.pneumacare.config.web.dto.SystemSettingResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.util.List;

/**
 * Application service for the centralized configuration hub. Reads and updates
 * system-wide settings; only rows flagged {@code editable} may be changed.
 */
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository repository;

    @Transactional(readOnly = true)
    public List<SystemSettingResponse> listAll() {
        return repository.findAllByOrderByCategoryAscSettingKeyAsc()
                .stream()
                .map(SystemSettingResponse::from)
                .toList();
    }

    @Transactional
    public SystemSettingResponse update(String settingKey, String value) {
        SystemSettingJpaEntity setting = repository.findById(settingKey)
                .orElseThrow(() -> new BusinessLayerException(
                        "No existe la configuración: " + settingKey, HttpStatus.NOT_FOUND));

        if (!setting.isEditable()) {
            throw new BusinessLayerException(
                    "Esta configuración no es editable: " + settingKey, HttpStatus.CONFLICT);
        }

        validateValue(setting.getValueType(), value);
        setting.setValue(value);
        return SystemSettingResponse.from(repository.save(setting));
    }

    /** Rejects values that don't match the setting's declared type before persisting. */
    private void validateValue(String valueType, String value) {
        switch (valueType) {
            case "number" -> {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    throw new BusinessLayerException("El valor debe ser numérico", HttpStatus.BAD_REQUEST);
                }
            }
            case "boolean" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new BusinessLayerException("El valor debe ser 'true' o 'false'", HttpStatus.BAD_REQUEST);
                }
            }
            default -> {
                /* text — no additional constraint beyond the DTO size limit */
            }
        }
    }
}
