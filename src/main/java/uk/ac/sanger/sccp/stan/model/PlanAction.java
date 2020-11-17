package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class PlanAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer planOperationId;

    @ManyToOne
    @JoinColumn(name="source_slot_id")
    private Slot source;
    @ManyToOne
    @JoinColumn(name="dest_slot_id")
    private Slot destination;
    @ManyToOne
    private PlanSample planSample;

    public PlanAction() {}

    public PlanAction(Integer id, Integer operationId, Slot source, Slot destination, PlanSample planSample) {
        this.id = id;
        this.planOperationId = operationId;
        this.source = source;
        this.destination = destination;
        this.planSample = planSample;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Slot getSource() {
        return this.source;
    }

    public void setSource(Slot source) {
        this.source = source;
    }

    public Slot getDestination() {
        return this.destination;
    }

    public void setDestination(Slot destination) {
        this.destination = destination;
    }

    public Integer getPlanOperationId() {
        return this.planOperationId;
    }

    public void setPlanOperationId(Integer planOperationId) {
        this.planOperationId = planOperationId;
    }

    public PlanSample getPlanSample() {
        return this.planSample;
    }

    public void setPlanSample(PlanSample planSample) {
        this.planSample = planSample;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanAction that = (PlanAction) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.planOperationId, that.planOperationId)
                && Objects.equals(this.source, that.source)
                && Objects.equals(this.destination, that.destination)
                && Objects.equals(this.planSample, that.planSample));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("planOperationId", planOperationId)
                .add("source", source)
                .add("destination", destination)
                .add("planSample", planSample)
                .toString();
    }
}
