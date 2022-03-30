package uk.ac.sanger.sccp.stan.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.*;

/**
 * A work (identified by a work number) indicates a piece of requested work to be performed for some particular project and cost code
 * @author dr6
 */
@Entity
public class Work {
    // region inner classes
    public enum Status {
        unstarted, active, paused, completed, failed,withdrawn
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

    private String workNumber;

    @ManyToOne
    private WorkType workType;

    @ManyToOne
    private Project project;

    @ManyToOne
    private CostCode costCode;

    @Column(columnDefinition = "enum('unstarted', 'active', 'paused', 'completed', 'failed','withdrawn')")
    @Enumerated(EnumType.STRING)
    private Status status;

    @ElementCollection
    @CollectionTable(name="work_op", joinColumns=@JoinColumn(name="work_id"))
    @Column(name="operation_id")
    private List<Integer> operationIds;

    @ElementCollection
    @CollectionTable(name="work_sample", joinColumns=@JoinColumn(name="work_id"))
    private List<SampleSlotId> sampleSlotIds;

    private Integer numBlocks, numSlides;

    private String priority;

    public Work() {}

    public Work(Integer id, String workNumber, WorkType workType, Project project, CostCode costCode, Status status,
                Integer numBlocks, Integer numSlides, String priority) {
        this.id = id;
        this.workNumber = workNumber;
        this.workType = workType;
        this.project = project;
        this.costCode = costCode;
        this.status = status;
        this.numBlocks = numBlocks;
        this.numSlides = numSlides;
        this.priority = priority;
    }

    public Work(Integer id, String workNumber, WorkType workType, Project project, CostCode costCode, Status status) {
        this(id, workNumber, workType, project, costCode, status, null, null, null);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    public WorkType getWorkType() {
        return this.workType;
    }

    public void setWorkType(WorkType workType) {
        this.workType = workType;
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

    public Integer getNumBlocks() {
        return this.numBlocks;
    }

    public void setNumBlocks(Integer numBlocks) {
        this.numBlocks = numBlocks;
    }

    public Integer getNumSlides() {
        return this.numSlides;
    }

    public void setNumSlides(Integer numSlides) {
        this.numSlides = numSlides;
    }

    public String getPriority() {
        return this.priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    @JsonIgnore
    public boolean isClosed() {
        return (status==Status.completed || status==Status.failed || status== Status.withdrawn);
    }

    @JsonIgnore
    public boolean isOpen() {
        return !isClosed();
    }

    @JsonIgnore
    public boolean isUsable() {
        return (status==Status.active);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Work that = (Work) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.workType, that.workType)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.project, that.project)
                && Objects.equals(this.costCode, that.costCode)
                && Objects.equals(this.numBlocks, that.numBlocks)
                && Objects.equals(this.numSlides, that.numSlides)
                && Objects.equals(this.priority, that.priority)
                && this.status == that.status);
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : workNumber != null ? workNumber.hashCode() : 0);
    }

    @Override
    public String toString() {
        return this.workNumber;
    }
}
