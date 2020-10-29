package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class LabwareType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private int numRows = 1;
    private int numColumns = 1;

    @ManyToOne
    private LabelType labelType;

    public LabwareType() {}

    public LabwareType(Integer id, String name, int numRows, int numColumns, LabelType labelType) {
        this.id = id;
        this.name = name;
        this.numRows = numRows;
        this.numColumns = numColumns;
        this.labelType = labelType;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNumRows() {
        return this.numRows;
    }

    public void setNumRows(int numRows) {
        this.numRows = numRows;
    }

    public int getNumColumns() {
        return this.numColumns;
    }

    public void setNumColumns(int numColumns) {
        this.numColumns = numColumns;
    }

    public LabelType getLabelType() {
        return this.labelType;
    }

    public void setLabelType(LabelType labelType) {
        this.labelType = labelType;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabwareType that = (LabwareType) o;
        return (this.numRows == that.numRows
                && this.numColumns == that.numColumns
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.labelType, that.labelType));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }
}
