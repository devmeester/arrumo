package pt.arrumo.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A setting stored by the app. The value is encrypted: only the app can read it. */
@Entity
@Table(name = "app_setting")
public class Setting {
    @Id private String settingKey;
    private String encryptedValue;
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "updated_by") private String updatedBy;

    protected Setting() {}

    public Setting(String settingKey) { this.settingKey = settingKey; }

    public void set(String encryptedValue, String who) {
        this.encryptedValue = encryptedValue;
        this.updatedAt = Instant.now();
        this.updatedBy = who;
    }

    public String getSettingKey() { return settingKey; }
    public String getEncryptedValue() { return encryptedValue; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
