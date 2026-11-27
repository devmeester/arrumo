package pt.arrumo.packing;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "packing_card")
public class PackingCard {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "column_id") private Long columnId;
    private int sortOrder;
    private String title;
    private String product = "";
    private String quantity = "";
    private String notes = "";
    private boolean archived;
    @Column(name = "reported_at") private Instant reportedAt;
    @Column(name = "send_error") private String sendError;
    @Column(name = "created_at", updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at") private Instant updatedAt = Instant.now();
    @Version private long version;

    protected PackingCard() {}

    public PackingCard(Long columnId, int sortOrder, String title) {
        this.columnId = columnId;
        this.sortOrder = sortOrder;
        this.title = title;
    }

    public void touch() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getColumnId() { return columnId; }
    public void setColumnId(Long c) { this.columnId = c; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int o) { this.sortOrder = o; }
    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }
    public String getProduct() { return product; }
    public void setProduct(String p) { this.product = p == null ? "" : p; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String q) { this.quantity = q == null ? "" : q; }
    public String getNotes() { return notes; }
    public void setNotes(String n) { this.notes = n == null ? "" : n; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean a) { this.archived = a; }
    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant t) { this.reportedAt = t; }
    public String getSendError() { return sendError; }
    public void setSendError(String e) { this.sendError = e; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
