package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * A label type for printing. Different labware have different label types, and the label types
 * indicate the dimensions and contents of the label
 * @author dr6
 */
@Entity
public class LabelType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;

    public LabelType() {}

    public LabelType(Integer id, String name) {
        this.id = id;
        this.name = name;
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

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabelType that = (LabelType) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }
}
