package wfederico.pneumacare.config.web.dto;

import wfederico.pneumacare.config.domain.SettingCategory;
import wfederico.pneumacare.config.infrastructure.persistence.SystemSettingJpaEntity;

/** Read model for one system setting in the centralized configuration hub. */
public record SystemSettingResponse(
        String settingKey,
        String value,
        SettingCategory category,
        String label,
        String description,
        String valueType,
        boolean editable) {

    public static SystemSettingResponse from(SystemSettingJpaEntity e) {
        return new SystemSettingResponse(
                e.getSettingKey(),
                e.getValue(),
                e.getCategory(),
                e.getLabel(),
                e.getDescription(),
                e.getValueType(),
                e.isEditable());
    }
}
