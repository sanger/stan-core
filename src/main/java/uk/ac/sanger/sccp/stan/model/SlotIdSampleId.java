package uk.ac.sanger.sccp.stan.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * This class is used as a composite primary key
 * @author dr6
 */
public class SlotIdSampleId implements Serializable {
    private Integer slotId;
    private Integer sampleId;

    public SlotIdSampleId() {}

    public SlotIdSampleId(Slot slot, Sample sample) {
        this(slot.getId(), sample.getId());
    }

    public SlotIdSampleId(Integer slotId, Integer sampleId) {
        this.slotId = slotId;
        this.sampleId = sampleId;
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
    public String toString() {
        return String.format("(slotId=%s, sampleId=%s)", slotId, sampleId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotIdSampleId that = (SlotIdSampleId) o;
        return (Objects.equals(this.slotId, that.slotId)
                && Objects.equals(this.sampleId, that.sampleId));
    }

    @Override
    public int hashCode() {
        return (slotId==null ? 0 : slotId.hashCode() * 31) + (sampleId==null ? 0 : sampleId.hashCode());
    }
}
