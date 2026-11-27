package pt.arrumo.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Finished-goods pallet, usually identified by the SSCC on its GS1 label. */
@Entity
@Table(name = "pallet")
public class Pallet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String sscc;
    private String lot;
    private String gtin;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "product_id") private Product product;
    @Column(name = "weight_kg") private BigDecimal weightKg;
    private Integer boxes;
    @Column(name = "production_date") private LocalDate productionDate;
    private LocalDate expiryDate;
    private String notes = "";
    @Enumerated(EnumType.STRING) private Lot.Status status = Lot.Status.IN_STOCK;
    @Column(name = "shipped_at") private Instant shippedAt;
    @Version private long version;

    protected Pallet() {}

    public Pallet(String sscc, String lot, String gtin) {
        this.sscc = sscc;
        this.lot = lot;
        this.gtin = gtin;
        this.productionDate = Lot.dateFromCode(lot);
    }

    public void ship() { this.status = Lot.Status.SHIPPED; this.shippedAt = Instant.now(); }
    public void restore() { this.status = Lot.Status.IN_STOCK; this.shippedAt = null; }

    /** How it shows up on the map and in movements: the lot, or the SSCC, or the internal id. */
    public String name() { return lot != null ? lot : sscc != null ? sscc : "Palete " + id; }

    public Long getId() { return id; }
    public String getSscc() { return sscc; }
    public String getLot() { return lot; }
    public void setLot(String l) { this.lot = l; }
    public String getGtin() { return gtin; }
    public void setGtin(String g) { this.gtin = g; }
    public Product getProduct() { return product; }
    public void setProduct(Product p) { this.product = p; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal kg) { this.weightKg = kg; }
    public Integer getBoxes() { return boxes; }
    public void setBoxes(Integer b) { this.boxes = b; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate d) { this.productionDate = d; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate d) { this.expiryDate = d; }
    public String getNotes() { return notes; }
    public void setNotes(String n) { this.notes = n == null ? "" : n; }
    public Lot.Status getStatus() { return status; }
    public long getVersion() { return version; }
}
