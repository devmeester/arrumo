package pt.arrumo.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "location")
public class Location {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String code;
    @Column(name = "zone_id") private String zoneId;
    private String groupLabel;
    private int rowNo;
    private int colNo;
    @OneToOne(fetch = FetchType.EAGER) @JoinColumn(name = "lot_id") private Lot lot;
    @OneToOne(fetch = FetchType.EAGER) @JoinColumn(name = "pallet_id") private Pallet pallet;
    private String note;
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "updated_by") private String updatedBy;
    @Version private long version;

    protected Location() {}

    public boolean isFree() { return lot == null && pallet == null && note == null; }
    public boolean isFinishedGoods() { return "fg".equals(zoneId); }

    public void touch(String username) {
        this.updatedAt = Instant.now();
        this.updatedBy = username;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getZoneId() { return zoneId; }
    public String getGroupLabel() { return groupLabel; }
    public int getRowNo() { return rowNo; }
    public int getColNo() { return colNo; }
    public Lot getLot() { return lot; }
    public void setLot(Lot l) { this.lot = l; }
    public Pallet getPallet() { return pallet; }
    public void setPallet(Pallet p) { this.pallet = p; }
    public String getNote() { return note; }
    public void setNote(String n) { this.note = n; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }

    /** The code is the name people see: R514a, R521c, R324. */
    public String name() { return code; }

    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("(?i)^R(\\d{3})([a-c]?)$");

    /** Scanned or imported text -> code: " r514A " -> "R514a". Any other text is only trimmed. */
    public static String normalize(String text) {
        if (text == null) return "";
        String t = text.strip();
        var m = CODE.matcher(t);
        return m.matches() ? "R" + m.group(1) + m.group(2).toLowerCase(java.util.Locale.ROOT) : t;
    }
}
