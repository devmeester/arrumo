package pt.arrumo.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable record of every map operation (the database rejects UPDATE and DELETE). */
@Entity
@Table(name = "movement")
public class Movement {
    public enum Type { STORE, MOVE, SHIP, REMOVE, ADD_NOTE, REMOVE_NOTE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(updatable = false) private Instant occurredAt = Instant.now();
    @Column(updatable = false) private String username;
    @Enumerated(EnumType.STRING) @Column(updatable = false) private Type type;
    @Column(name = "lot_code", updatable = false) private String lotCode;
    @Column(name = "from_location", updatable = false) private String fromLocation;
    @Column(name = "to_location", updatable = false) private String toLocation;
    @Column(updatable = false) private String note = "";
    @Column(updatable = false) private String area = "RM";

    protected Movement() {}

    public Movement(String username, Type type, String lotCode, String from, String to, String note) {
        this.username = username;
        this.type = type;
        this.lotCode = lotCode;
        this.fromLocation = from;
        this.toLocation = to;
        this.note = note == null ? "" : note;
    }

    /** Marks the movement as belonging to the finished-goods map. */
    public Movement inFinishedGoods() { this.area = "FG"; return this; }
    public String getArea() { return area; }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getUsername() { return username; }
    public Type getType() { return type; }
    public String getLotCode() { return lotCode; }
    public String getFromLocation() { return fromLocation; }
    public String getToLocation() { return toLocation; }
    public String getNote() { return note; }
}
