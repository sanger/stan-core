package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
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

    public Sample() {}

    public Sample(Integer id, Integer section, Tissue tissue) {
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
        Sample that = (Sample) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.section, that.section));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }
}
