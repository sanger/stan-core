package uk.ac.sanger.sccp.stan.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.*;

/**
 * A work (identified by a work number) indicates a piece of requested work to be performed for some particular project and cost code
 * @author dr6
 */
@Entity
public class Work {
    // region inner classes
    /** The states that a particular work may go through */
    public enum Status {
        unstarted, active, paused, completed, failed, withdrawn
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
    private ReleaseRecipient workRequester;
    
    @ManyToOne
    private Project project;

    @ManyToOne
    private Program program;

    @ManyToOne
    private CostCode costCode;

    @ManyToOne
    private OmeroProject omeroProject;

    @ManyToOne
    private DnapStudy dnapStudy;

    @Column(columnDefinition = "enum('unstarted', 'active', 'paused', 'completed', 'failed', 'withdrawn')")
    @Enumerated(EnumType.STRING)
    private Status status;

    @ElementCollection
    @CollectionTable(name="work_op", joinColumns=@JoinColumn(name="work_id"))
    @Column(name="operation_id")
    private Set<Integer> operationIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name="work_release", joinColumns=@JoinColumn(name="work_id"))
    @Column(name="release_id")
    private Set<Integer> releaseIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name="work_sample", joinColumns=@JoinColumn(name="work_id"))
    private Set<SampleSlotId> sampleSlotIds = new HashSet<>();

    private Integer numBlocks, numSlides, numOriginalSamples;

    private String priority;

    public Work() {}

    public Work(Integer id, String workNumber, WorkType workType, ReleaseRecipient workRequester, Project project, Program program, CostCode costCode, Status status,
                Integer numBlocks, Integer numSlides, Integer numOriginalSamples, String priority, OmeroProject omeroProject, DnapStudy dnapStudy) {
        this.id = id;
        this.workNumber = workNumber;
        this.workType = workType;
        this.workRequester = workRequester;
        this.project = project;
        this.program = program;
        this.costCode = costCode;
        this.status = status;
        this.numBlocks = numBlocks;
        this.numSlides = numSlides;
        this.numOriginalSamples = numOriginalSamples;
        this.priority = priority;
        this.omeroProject = omeroProject;
        this.dnapStudy = dnapStudy;
        setOperationIds(null);
        setReleaseIds(null);
    }

    public Work(Integer id, String workNumber, WorkType workType, ReleaseRecipient workRequester, Project project,
                Program program, CostCode costCode, Status status) {
        this(id, workNumber, workType, workRequester, project, program, costCode, status, null,
                null, null, null, null, null);
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

    public ReleaseRecipient getWorkRequester() {
        return this.workRequester;
    }

    public void setWorkRequester(ReleaseRecipient workRequester) {
        this.workRequester = workRequester;
    }

    public Project getProject() {
        return this.project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Program getProgram() {
        return this.program;
    }

    public void setProgram(Program program) {
        this.program = program;
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

    /** The ids of operations linked to this work */
    public Set<Integer> getOperationIds() {
        return this.operationIds;
    }

    public void setOperationIds(Set<Integer> operationIds) {
        this.operationIds = operationIds==null ? new HashSet<>() : operationIds;
    }

    /** The ids of releases linked to this work */
    public Set<Integer> getReleaseIds() {
        return this.releaseIds;
    }

    public void setReleaseIds(Set<Integer> releaseIds) {
        this.releaseIds = releaseIds==null ? new HashSet<>() : releaseIds;
    }

    /** The ids of samples and slots that are in combination linked to this work */
    public Set<SampleSlotId> getSampleSlotIds() {
        return this.sampleSlotIds;
    }

    public void setSampleSlotIds(Set<SampleSlotId> sampleSlotIds) {
        this.sampleSlotIds = sampleSlotIds==null ? new HashSet<>() : sampleSlotIds;
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

    public Integer getNumOriginalSamples() {
        return this.numOriginalSamples;
    }

    public void setNumOriginalSamples(Integer numOriginalSamples) {
        this.numOriginalSamples = numOriginalSamples;
    }

    public String getPriority() {
        return this.priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public OmeroProject getOmeroProject() {
        return this.omeroProject;
    }

    public void setOmeroProject(OmeroProject omeroProject) {
        this.omeroProject = omeroProject;
    }

    public DnapStudy getDnapStudy() {
        return this.dnapStudy;
    }

    public void setDnapStudy(DnapStudy dnapStudy) {
        this.dnapStudy = dnapStudy;
    }

    @JsonIgnore
    public boolean isClosed() {
        return (status==Status.completed || status==Status.failed || status==Status.withdrawn);
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
                && Objects.equals(this.workRequester, that.workRequester)
                && Objects.equals(this.project, that.project)
                && Objects.equals(this.program, that.program)
                && Objects.equals(this.costCode, that.costCode)
                && Objects.equals(this.numBlocks, that.numBlocks)
                && Objects.equals(this.numSlides, that.numSlides)
                && Objects.equals(this.numOriginalSamples, that.numOriginalSamples)
                && Objects.equals(this.priority, that.priority)
                && Objects.equals(this.omeroProject, that.omeroProject)
                && Objects.equals(this.dnapStudy, that.dnapStudy)
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
