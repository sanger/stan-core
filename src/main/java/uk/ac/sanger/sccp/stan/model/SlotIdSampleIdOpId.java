package uk.ac.sanger.sccp.stan.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * This class is used as a composite primary key
 * @author dr6
 */
public class SlotIdSampleIdOpId implements Serializable {
    private Integer slotId;
    private Integer sampleId;
    private Integer operationId;

    public SlotIdSampleIdOpId() {}

    public SlotIdSampleIdOpId(Integer slotId, Integer sampleId, Integer operationId) {
        this.slotId = slotId;
        this.sampleId = sampleId;
        this.operationId = operationId;
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

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotIdSampleIdOpId that = (SlotIdSampleIdOpId) o;
        return (Objects.equals(this.slotId, that.slotId)
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.operationId, that.operationId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotId, sampleId, operationId);
    }

    @Override
    public String toString() {
        return String.format("(slotId=%s, sampleId=%s, operationId=%s)",
                slotId, sampleId, operationId);
    }
}
