package pt.arrumo.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** One row of the finished-goods stock exported by the ERP. It is the total for the lot, not for a pallet. */
@Entity
@Table(name = "erp_fg_stock")
public class ErpFgStock {
    @Id private String lot;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") private Product product;
    private String quantity;
    @Column(name = "weight_kg") private BigDecimal weightKg;
    private BigDecimal boxes;
    private String notes = "";
    @Column(name = "imported_at") private Instant importedAt = Instant.now();

    protected ErpFgStock() {}

    public ErpFgStock(String lot, Product product, String quantity, BigDecimal weightKg, BigDecimal boxes, String notes) {
        this.lot = lot;
        this.product = product;
        this.quantity = quantity;
        this.weightKg = weightKg;
        this.boxes = boxes;
        this.notes = notes;
    }

    public String getLot() { return lot; }
    public Product getProduct() { return product; }
    public String getQuantity() { return quantity; }
    public BigDecimal getWeightKg() { return weightKg; }
    public BigDecimal getBoxes() { return boxes; }
    public String getNotes() { return notes; }
    public Instant getImportedAt() { return importedAt; }
}
