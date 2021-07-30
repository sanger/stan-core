package uk.ac.sanger.sccp.stan.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.*;

/**
 * An SAS number identifies a piece of requested work to be performed for some particular project and cost code
 * @author dr6
 */
@Entity
public class SasNumber {
    // region inner classes
    public enum Status {
        active, paused, completed, failed
    }
    @Embeddable
    public static class SampleSlotId {
        private Integer sampleId;
        private Integer slotId;

        public SampleSlotId() {}

        public SampleSlotId(Integer sampleId, Integer slotId) {
            this.sampleId = sampleId;
            this.slotId = slotId;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SampleSlotId that = (SampleSlotId) o;
            return (Objects.equals(this.sampleId, that.sampleId)
                    && Objects.equals(this.slotId, that.slotId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(slotId, sampleId);
        }

        @Override
        public String toString() {
            return String.format("(sampleId=%s, slotId=%s)", sampleId, slotId);
        }
    }
    // endregion

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String sasNumber;

    @ManyToOne
    private Project project;

    @ManyToOne
    private CostCode costCode;

    @Column(columnDefinition = "enum('active', 'paused', 'completed', 'failed')")
    @Enumerated(EnumType.STRING)
    private Status status;

    @ElementCollection
    @CollectionTable(name="sas_op", joinColumns=@JoinColumn(name="sas_id"))
    @Column(name="operation_id")
    private List<Integer> operationIds;

    @ElementCollection
    @CollectionTable(name="sas_sample", joinColumns=@JoinColumn(name="sas_id"))
    private List<SampleSlotId> sampleSlotIds;

    public SasNumber() {}

    public SasNumber(Integer id, String sasNumber, Project project, CostCode costCode, Status status) {
        this.id = id;
        this.sasNumber = sasNumber;
        this.project = project;
        this.costCode = costCode;
        this.status = status;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSasNumber() {
        return this.sasNumber;
    }

    public void setSasNumber(String sasNumber) {
        this.sasNumber = sasNumber;
    }

    public Project getProject() {
        return this.project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public CostCode getCostCode() {
        return this.costCode;
    }

    public void setCostCode(CostCode costCode) {
        this.costCode = costCode;
    }

    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public List<Integer> getOperationIds() {
        return this.operationIds;
    }

    public void setOperationIds(List<Integer> operationIds) {
        this.operationIds = (operationIds instanceof ArrayList ? operationIds : BasicUtils.newArrayList(operationIds));
    }

    public List<SampleSlotId> getSampleSlotIds() {
        return this.sampleSlotIds;
    }

    public void setSampleSlotIds(List<SampleSlotId> sampleSlotIds) {
        this.sampleSlotIds = (sampleSlotIds instanceof ArrayList ? sampleSlotIds : BasicUtils.newArrayList(sampleSlotIds));
    }

    @JsonIgnore
    public boolean isClosed() {
        return (status==Status.completed || status==Status.failed);
    }

    @JsonIgnore
    public boolean isOpen() {
        return !isClosed();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SasNumber that = (SasNumber) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.sasNumber, that.sasNumber)
                && Objects.equals(this.project, that.project)
                && Objects.equals(this.costCode, that.costCode)
                && this.status == that.status);
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : sasNumber != null ? sasNumber.hashCode() : 0);
    }

    @Override
    public String toString() {
        return this.sasNumber;
    }
}
