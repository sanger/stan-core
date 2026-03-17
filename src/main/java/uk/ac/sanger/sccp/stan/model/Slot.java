package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * An addressed position inside labware that may contain samples
 * @author dr6
 */
@Entity
public class Slot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name="labware_id")
    private Integer labwareId;
    @Embedded
    private Address address;
    @ManyToMany
    @JoinTable(name = "slot_sample", inverseJoinColumns = @JoinColumn(name="sample_id"))
    private List<Sample> samples;

    public Slot() {
        this.samples = new ArrayList<>();
    }

    public Slot(Integer id, Integer labwareId, Address address, List<Sample> samples) {
        this.id = id;
        this.labwareId = labwareId;
        this.address = address;
        setSamples(samples);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getLabwareId() {
        return this.labwareId;
    }

    public void setLabwareId(Integer labwareId) {
        this.labwareId = labwareId;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public List<Sample> getSamples() {
        return this.samples;
    }

    public void setSamples(List<Sample> samples) {
        this.samples = newArrayList(samples);
    }

    /**
     * Adds a sample to this slot's list of samples. No validation.
     */
    public void addSample(Sample sample) {
        this.samples.add(sample);
    }

    public boolean isBlock() {
        return (this.samples.stream().anyMatch(Sample::isBlock));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Slot that = (Slot) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.address, that.address)
                && Objects.equals(this.labwareId, that.labwareId)
                && Objects.equals(this.samples, that.samples)
        );
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : Objects.hash(labwareId, address));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("labwareId", labwareId)
                .add("address", address)
                .add("samples", samples)
                .toString();
    }
}
