package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.Objects;

/**
 * The position of a sample inside a slot.
 * @author dr6
 */
@Entity
@IdClass(SlotIdSampleId.class)
public class SamplePosition {
    @Id
    private Integer slotId;
    @Id
    private Integer sampleId;

    @ManyToOne
    private SlotRegion slotRegion;

    private Integer operationId;

    public SamplePosition() {}

    public SamplePosition(Integer slotId, Integer sampleId, SlotRegion slotRegion, Integer operationId) {
        this.slotId = slotId;
        this.sampleId = sampleId;
        this.slotRegion = slotRegion;
        this.operationId = operationId;
    }

    /** The id of the slot */
    public Integer getSlotId() {
        return this.slotId;
    }

    public void setSlotId(Integer slotId) {
        this.slotId = slotId;
    }

    /** The id of the sample */
    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public SlotRegion getSlotRegion() {
        return this.slotRegion;
    }

    public void setSlotRegion(SlotRegion slotRegion) {
        this.slotRegion = slotRegion;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
    }

    @Override
    public String toString() {
        return BasicUtils.describe("SamplePosition")
                .add("slotId", slotId)
                .add("sampleId", sampleId)
                .add("slotRegion", slotRegion)
                .add("operationId", operationId)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SamplePosition that = (SamplePosition) o;
        return (Objects.equals(this.slotId, that.slotId)
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.slotRegion, that.slotRegion)
                && Objects.equals(this.operationId, that.operationId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotId, sampleId);
    }
}
