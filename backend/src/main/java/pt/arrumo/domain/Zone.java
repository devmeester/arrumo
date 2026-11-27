package pt.arrumo.domain;

import jakarta.persistence.*;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "zone")
public class Zone {
    @Id private String id;
    private String name;
    private int sortOrder;
    private String columnLabels;

    protected Zone() {}

    public String getId() { return id; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
    public List<String> getColumnLabels() { return Arrays.asList(columnLabels.split(";")); }
}
