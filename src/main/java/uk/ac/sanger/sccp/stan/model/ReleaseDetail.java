package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
@Table(name = "release_detail")
public class ReleaseDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name="release_id")
    private Integer releaseId;

    @Column(name="slot_id")
    private Integer slotId;

    @Column(name="sample_id")
    private Integer sampleId;

    public ReleaseDetail() {}

    public ReleaseDetail(Integer id, Integer releaseId, Integer slotId, Integer sampleId) {
        this.id = id;
        this.releaseId = releaseId;
        this.slotId = slotId;
        this.sampleId = sampleId;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getReleaseId() {
        return this.releaseId;
    }

    public void setReleaseId(Integer releaseId) {
        this.releaseId = releaseId;
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
        ReleaseDetail that = (ReleaseDetail) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.releaseId, that.releaseId)
                && Objects.equals(this.slotId, that.slotId)
                && Objects.equals(this.sampleId, that.sampleId));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : Objects.hash(releaseId, slotId, sampleId));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("releaseId", releaseId)
                .add("slotId", slotId)
                .add("sampleId", sampleId)
                .toString();
    }
}
