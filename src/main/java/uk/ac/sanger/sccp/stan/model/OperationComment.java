package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class OperationComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    private Comment comment;

    private Integer operationId;
    private Integer sampleId;
    private Integer slotId;
    private Integer labwareId;

    public OperationComment() {}

    public OperationComment(Integer id, Comment comment, Integer operationId, Integer sampleId, Integer slotId, Integer labwareId) {
        this.id = id;
        this.comment = comment;
        this.operationId = operationId;
        this.sampleId = sampleId;
        this.slotId = slotId;
        this.labwareId = labwareId;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Comment getComment() {
        return this.comment;
    }

    public void setComment(Comment comment) {
        this.comment = comment;
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

    public Integer getLabwareId() {
        return this.labwareId;
    }

    public void setLabwareId(Integer labwareId) {
        this.labwareId = labwareId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationComment that = (OperationComment) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.comment, that.comment)
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.slotId, that.slotId)
                && Objects.equals(this.labwareId, that.labwareId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, comment, operationId, sampleId, slotId, labwareId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("comment", comment)
                .add("operationId", operationId)
                .add("sampleId", sampleId)
                .add("slotId", slotId)
                .add("labwareId", labwareId)
                .toString();
    }
}
