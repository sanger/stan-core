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
    private Integer newSection;
    private Integer sampleThickness;
    @ManyToOne
    private BioState newBioState;

    public PlanAction() {}

    public PlanAction(Integer id, Integer planOperationId, Slot source, Slot destination, Sample sample) {
        this(id, planOperationId, source, destination, sample, null, null, null);
    }

    public PlanAction(Integer id, Integer planOperationId, Slot source, Slot destination, Sample sample,
                      Integer newSection, Integer sampleThickness, BioState newBioState) {
        this.id = id;
        this.planOperationId = planOperationId;
        this.source = source;
        this.destination = destination;
        this.sample = sample;
        this.newSection = newSection;
        this.sampleThickness = sampleThickness;
        this.newBioState = newBioState;
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

    public Integer getNewSection() {
        return this.newSection;
    }

    public void setNewSection(Integer section) {
        this.newSection = section;
    }

    public Integer getSampleThickness() {
        return this.sampleThickness;
    }

    public void setSampleThickness(Integer sampleThickness) {
        this.sampleThickness = sampleThickness;
    }

    public BioState getNewBioState() {
        return this.newBioState;
    }

    public void setNewBioState(BioState newBioState) {
        this.newBioState = newBioState;
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
                && Objects.equals(this.sample, that.sample)
                && Objects.equals(this.newSection, that.newSection)
                && Objects.equals(this.sampleThickness, that.sampleThickness)
                && Objects.equals(this.newBioState, that.newBioState));
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
                .add("newSection", newSection)
                .add("sampleThickness", sampleThickness)
                .add("newBioState", newBioState)
                .toString();
    }
}
