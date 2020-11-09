package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.*;

/**
 * @author dr6
 */
@Entity
@SecondaryTable(name = "block_info", pkJoinColumns = @PrimaryKeyJoinColumn(name = "slot_id"))
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

    @Column(table = "block_info", name = "sample_id")
    private Integer blockSampleId;
    @Column(table = "block_info", name = "highest_section")
    private Integer blockHighestSection;

    public Slot() {
        this.samples = new ArrayList<>();
    }

    public Slot(Integer id, Integer labwareId, Address address, List<Sample> samples, Integer blockSampleId,
                Integer blockHighestSection) {
        this.id = id;
        this.labwareId = labwareId;
        this.address = address;
        this.samples = samples;
        this.blockSampleId = blockSampleId;
        this.blockHighestSection = blockHighestSection;
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
        this.samples = samples;
    }

    public Integer getBlockSampleId() {
        return this.blockSampleId;
    }

    public void setBlockSampleId(Integer blockSampleId) {
        this.blockSampleId = blockSampleId;
    }

    public Integer getBlockHighestSection() {
        return this.blockHighestSection;
    }

    public void setBlockHighestSection(Integer blockHighestSection) {
        this.blockHighestSection = blockHighestSection;
    }

    public boolean isBlock() {
        return (this.blockSampleId != null);
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
                && Objects.equals(this.blockSampleId, that.blockSampleId)
                && Objects.equals(this.blockHighestSection, that.blockHighestSection));
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
                .add("blockSampleId", blockSampleId)
                .add("blockHighestSection", blockHighestSection)
                .toString();
    }
}
