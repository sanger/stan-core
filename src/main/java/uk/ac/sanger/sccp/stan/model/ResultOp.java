package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.Objects;

/**
 * A result linked to an operation
 * @author dr6
 */
@Entity
public class ResultOp {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "enum('pass', 'fail')")
    @Enumerated(EnumType.STRING)
    private PassFail result;

    private Integer operationId;
    private Integer sampleId;
    private Integer slotId;
    private Integer refersToOpId;

    public ResultOp() {}

    public ResultOp(Integer id, PassFail result, Integer operationId, Integer sampleId, Integer slotId,
                    Integer refersToOpId) {
        this.id = id;
        this.result = result;
        this.operationId = operationId;
        this.sampleId = sampleId;
        this.slotId = slotId;
        this.refersToOpId = refersToOpId;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public PassFail getResult() {
        return this.result;
    }

    public void setResult(PassFail result) {
        this.result = result;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public Integer getSlotId() {
        return this.slotId;
    }

    public void setSlotId(Integer slotId) {
        this.slotId = slotId;
    }

    public Integer getRefersToOpId() {
        return this.refersToOpId;
    }

    public void setRefersToOpId(Integer refersToOpId) {
        this.refersToOpId = refersToOpId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResultOp that = (ResultOp) o;
        return (Objects.equals(this.id, that.id)
                && this.result == that.result
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.slotId, that.slotId)
                && Objects.equals(this.refersToOpId, that.refersToOpId));
    }

    @Override
    public int hashCode() {
        return id!=null ? id.hashCode() : Objects.hash(operationId, sampleId, slotId);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("id", id)
                .add("result", result)
                .add("operationId", operationId)
                .add("sampleId", sampleId)
                .add("slotId", slotId)
                .addIfNotNull("refersToOpId", refersToOpId)
                .toString();
    }
}
