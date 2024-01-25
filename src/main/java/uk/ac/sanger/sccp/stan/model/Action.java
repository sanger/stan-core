package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * An action is part of an operation, linking a source slot/sample to a destination slot/sample.
 * For operations that happen in place, the source and destination are typically the same slot.
 * @author dr6
 */
@Entity
public class Action {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name="operation_id")
    private Integer operationId;

    @ManyToOne
    @JoinColumn(name="source_slot_id")
    private Slot source;
    @ManyToOne
    @JoinColumn(name="dest_slot_id")
    private Slot destination;
    @ManyToOne
    private Sample sample;
    @ManyToOne
    private Sample sourceSample;

    public Action() {}

    public Action(Integer id, Integer operationId, Slot source, Slot destination, Sample sample, Sample sourceSample) {
        this.id = id;
        this.operationId = operationId;
        this.source = source;
        this.destination = destination;
        this.sample = sample;
        this.sourceSample = sourceSample;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
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

    /** The sample involved in this operation in the destination slot */
    public Sample getSample() {
        return this.sample;
    }

    public void setSample(Sample sample) {
        this.sample = sample;
    }

    /** The sample in the source slot that was used as input to the operation */
    public Sample getSourceSample() {
        return this.sourceSample;
    }

    public void setSourceSample(Sample sourceSample) {
        this.sourceSample = sourceSample;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Action that = (Action) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.source, that.source)
                && Objects.equals(this.destination, that.destination)
                && Objects.equals(this.sample, that.sample)
                && Objects.equals(this.sourceSample, that.sourceSample));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("operationId", operationId)
                .add("source", source)
                .add("destination", destination)
                .add("sample", sample)
                .add("sourceSample", sourceSample)
                .toString();
    }
}
