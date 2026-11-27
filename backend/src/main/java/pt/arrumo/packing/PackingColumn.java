package pt.arrumo.packing;

import jakarta.persistence.*;

@Entity
@Table(name = "packing_column")
public class PackingColumn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name;
    private int sortOrder;
    @Column(name = "daily_list") private boolean dailyList;
    private boolean reports;

    protected PackingColumn() {}

    public PackingColumn(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int o) { this.sortOrder = o; }
    public boolean isDailyList() { return dailyList; }
    public void setDailyList(boolean v) { this.dailyList = v; }
    public boolean isReports() { return reports; }
    public void setReports(boolean v) { this.reports = v; }
}
