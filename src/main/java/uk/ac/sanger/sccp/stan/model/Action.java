package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class Action {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer operationId;

    @ManyToOne
    @JoinColumn(name="source_slot_id")
    private Slot source;
    @ManyToOne
    @JoinColumn(name="dest_slot_id")
    private Slot destination;
    @ManyToOne
    private Sample sample;

    public Action() {}

    public Action(Integer id, Integer operationId, Slot source, Slot destination, Sample sample) {
        this.id = id;
        this.operationId = operationId;
        this.source = source;
        this.destination = destination;
        this.sample = sample;
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

    public Sample getSample() {
        return this.sample;
    }

    public void setSample(Sample sample) {
        this.sample = sample;
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
                .add("operationId", operationId)
                .add("source", source)
                .add("destination", destination)
                .add("sample", sample)
                .toString();
    }
}
