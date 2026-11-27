package pt.arrumo.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@Table(name = "lot")
public class Lot {
    public enum Status { IN_STOCK, SHIPPED }

    /** Lists without a location: corridor and leftovers. */
    public enum Holding { CORRIDOR, LEFTOVERS }

    /** ERP lot codes start with L + YYMMDD (e.g. L261121000009 = 21/11/2026). */
    private static final Pattern CODE_WITH_DATE = Pattern.compile("^L(\\d{2})(\\d{2})(\\d{2})");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String code;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "product_id") private Product product;
    @Column(name = "weight_kg") private BigDecimal weightKg;
    @Column(name = "production_date") private LocalDate productionDate;
    private String notes = "";
    @Enumerated(EnumType.STRING) private Status status = Status.IN_STOCK;
    @Enumerated(EnumType.STRING) private Holding holding;
    @Column(name = "shipped_at") private Instant shippedAt;
    @Version private long version;

    protected Lot() {}

    public Lot(String code, Product product, BigDecimal weightKg) {
        this.code = code;
        this.product = product;
        this.weightKg = weightKg;
        this.productionDate = dateFromCode(code);
    }

    public static LocalDate dateFromCode(String code) {
        Matcher m = CODE_WITH_DATE.matcher(code == null ? "" : code);
        if (!m.find()) return null;
        try {
            return LocalDate.of(2000 + Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    /** Lot scanned into the map that has no product yet: shown in orange until someone fills in the data. */
    public boolean noData() { return product == null; }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Product getProduct() { return product; }
    public void setProduct(Product p) { this.product = p; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal kg) { this.weightKg = kg; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate d) { this.productionDate = d; }
    public String getNotes() { return notes; }
    public void setNotes(String n) { this.notes = n == null ? "" : n; }
    public Status getStatus() { return status; }
    public void ship() { this.status = Status.SHIPPED; this.shippedAt = Instant.now(); this.holding = null; }
    public void restore() { this.status = Status.IN_STOCK; this.shippedAt = null; }
    public Holding getHolding() { return holding; }
    public void setHolding(Holding h) { this.holding = h; }
    public Instant getShippedAt() { return shippedAt; }
}
