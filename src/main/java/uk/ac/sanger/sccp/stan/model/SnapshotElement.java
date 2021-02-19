package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * A record of a particular slot and sample in a snapshot
 * @author dr6
 */
@Entity
@Table(name="snapshot_element")
public class SnapshotElement {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    @Column(name="snapshot_id")
    private Integer snapshotId;
    private Integer slotId;
    private Integer sampleId;

    public SnapshotElement() {}

    public SnapshotElement(Integer id, Integer snapshotId, Integer slotId, Integer sampleId) {
        this.id = id;
        this.snapshotId = snapshotId;
        this.slotId = slotId;
        this.sampleId = sampleId;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getSnapshotId() {
        return this.snapshotId;
    }

    public void setSnapshotId(Integer snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Integer getSlotId() {
        return this.slotId;
    }

    public void setSlotId(Integer slotId) {
        this.slotId = slotId;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SnapshotElement that = (SnapshotElement) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.snapshotId, that.snapshotId)
                && Objects.equals(this.slotId, that.slotId)
                && Objects.equals(this.sampleId, that.sampleId));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : Objects.hash(snapshotId, slotId, sampleId));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("snapshotId", snapshotId)
                .add("slotId", slotId)
                .add("sampleId", sampleId)
                .toString();
    }
}
