package pt.arrumo.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "product")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String code;
    private String description;
    private String gtin;
    private boolean active = true;
    @Version private long version;

    protected Product() {}

    public Product(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String c) { this.code = c; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public String getGtin() { return gtin; }
    public void setGtin(String g) { this.gtin = g; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }
    public long getVersion() { return version; }
}
