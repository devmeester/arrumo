package pt.arrumo.packing;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "packing_dispatch")
public class PackingDispatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Instant occurredAt = Instant.now();
    private String type;
    private boolean ok;
    private String detail;

    protected PackingDispatch() {}

    public PackingDispatch(String type, boolean ok, String detail) {
        this.type = type;
        this.ok = ok;
        this.detail = detail == null ? "" : (detail.length() > 500 ? detail.substring(0, 500) : detail);
    }

    public Instant getOccurredAt() { return occurredAt; }
    public String getType() { return type; }
    public boolean isOk() { return ok; }
    public String getDetail() { return detail; }
}
