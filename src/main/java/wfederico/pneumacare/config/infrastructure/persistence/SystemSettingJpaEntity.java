package wfederico.pneumacare.config.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import wfederico.pneumacare.config.domain.SettingCategory;
import wfederico.pneumacare.shared.data.EntityBase;

/**
 * A single system-wide setting in the centralized configuration hub, stored as a
 * typed key/value row. {@code settingKey} is the stable identifier the code reads;
 * {@code value} is the current string-encoded value (callers parse as needed).
 *
 * <p>Editable only through the admin settings endpoint. {@code editable = false}
 * marks values that are surfaced for visibility but managed elsewhere.
 */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettingJpaEntity extends EntityBase {

    @Id
    @Column(name = "setting_key", nullable = false, updatable = false, length = 100)
    private String settingKey;

    @Column(name = "value", nullable = false, length = 500)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SettingCategory category;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "description", length = 500)
    private String description;

    /** Value type hint for the admin UI: {@code text}, {@code number}, or {@code boolean}. */
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    @Column(name = "editable", nullable = false)
    @Builder.Default
    private boolean editable = true;
}
