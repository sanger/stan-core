package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.Objects;

/**
 * A sample's region of interest
 * @author dr6
 */
@Entity
@IdClass(SlotIdSampleIdOpId.class)
public class Roi {
    @Id
    private Integer slotId;
    @Id
    private Integer sampleId;
    @Id
    private Integer operationId;

    private String roi;

    public Roi() {}

    public Roi(Integer slotId, Integer sampleId, Integer operationId, String roi) {
        this.slotId = slotId;
        this.sampleId = sampleId;
        this.operationId = operationId;
        this.roi = roi;
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

    public String getRoi() {
        return this.roi;
    }

    public void setRoi(String roi) {
        this.roi = roi;
    }

    @Override
    public String toString() {
        return BasicUtils.describe("Roi")
                .add("slotId", slotId)
                .add("sampleId", sampleId)
                .add("operationId", operationId)
                .add("roi", roi)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Roi that = (Roi) o;
        return (Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.slotId, that.slotId)
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.roi, that.roi));
    }

    @Override
    public int hashCode() {
        return Objects.hash(sampleId, slotId, operationId, roi);
    }
}
