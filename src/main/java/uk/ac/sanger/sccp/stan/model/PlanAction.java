package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * A planned action inside a planned operation.
 * @author dr6
 */
@Entity
@Table(name="plan_action")
public class PlanAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name="plan_operation_id")
    private Integer planOperationId;

    @ManyToOne
    @JoinColumn(name="source_slot_id")
    private Slot source;
    @ManyToOne
    @JoinColumn(name="dest_slot_id")
    private Slot destination;
    @ManyToOne
    private Sample sample;
    private int section;

    public PlanAction() {}

    public PlanAction(Integer id, Integer planOperationId, Slot source, Slot destination, Sample sample, int section) {
        this.id = id;
        this.planOperationId = planOperationId;
        this.source = source;
        this.destination = destination;
        this.sample = sample;
        this.section = section;
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

    public Sample getSample() {
        return this.sample;
    }

    public void setSample(Sample sample) {
        this.sample = sample;
    }

    public int getSection() {
        return this.section;
    }

    public void setSection(int section) {
        this.section = section;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanAction that = (PlanAction) o;
        return (Objects.equals(this.id, that.id)
                && this.section==that.section
                && Objects.equals(this.planOperationId, that.planOperationId)
                && Objects.equals(this.source, that.source)
                && Objects.equals(this.destination, that.destination)
                && Objects.equals(this.sample, that.sample));
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
                .add("sample", sample)
                .add("section", section)
                .toString();
    }
}
