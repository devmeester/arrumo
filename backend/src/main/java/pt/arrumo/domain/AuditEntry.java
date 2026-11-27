package pt.arrumo.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_entry")
public class AuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(updatable = false) private Instant occurredAt = Instant.now();
    @Column(updatable = false) private String username;
    @Column(updatable = false) private String ip;
    @Column(updatable = false) private String action;
    @Column(updatable = false) private String detail;

    protected AuditEntry() {}

    public AuditEntry(String username, String ip, String action, String detail) {
        this.username = username;
        this.ip = ip;
        this.action = action;
        this.detail = detail == null ? "" : (detail.length() > 500 ? detail.substring(0, 500) : detail);
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getUsername() { return username; }
    public String getIp() { return ip; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
}
