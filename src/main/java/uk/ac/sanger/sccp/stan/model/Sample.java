package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * A sample is a piece of some tissue that has some particular state and can be located inside slots in labware,
 * and used in operations.
 * @author dr6
 */
@Entity
public class Sample {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer section;
    @ManyToOne
    private Tissue tissue;
    @ManyToOne
    private BioState bioState;

    public Sample() {}

    public Sample(Integer id, Integer section, Tissue tissue, BioState bioState) {
        this.id = id;
        this.section = section;
        this.tissue = tissue;
        this.bioState = bioState;
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

    public BioState getBioState() {
        return this.bioState;
    }

    public void setBioState(BioState bioState) {
        this.bioState = bioState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sample that = (Sample) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.section, that.section)
                && Objects.equals(this.tissue, that.tissue)
                && Objects.equals(this.bioState, that.bioState));
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
                .add("bioState", bioState)
                .toString();
    }
}
