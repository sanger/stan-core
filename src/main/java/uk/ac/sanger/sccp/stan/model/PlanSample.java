package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class PlanSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer section;
    @ManyToOne
    private Tissue tissue;

    public PlanSample() {}

    public PlanSample(Integer id, Integer section, Tissue tissue) {
        this.id = id;
        this.section = section;
        this.tissue = tissue;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getSection() {
        return this.section;
    }

    public void setSection(Integer section) {
        this.section = section;
    }

    public Tissue getTissue() {
        return this.tissue;
    }

    public void setTissue(Tissue tissue) {
        this.tissue = tissue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanSample that = (PlanSample) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.section, that.section)
                && Objects.equals(this.tissue, that.tissue));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("section", section)
                .add("tissue", tissue)
                .toString();
    }
}
