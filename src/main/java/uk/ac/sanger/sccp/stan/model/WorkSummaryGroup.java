package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * A group in a work summary.
 * @author dr6
 */
public class WorkSummaryGroup {
    private WorkType workType;
    private Work.Status status;
    private int numWorks;
    private int totalLabwareRequired;

    public WorkSummaryGroup(WorkType workType, Work.Status status, int numWorks, int totalLabwareRequired) {
        this.workType = workType;
        this.status = status;
        this.numWorks = numWorks;
        this.totalLabwareRequired = totalLabwareRequired;
    }

    public WorkSummaryGroup() {}

    /**
     * The work type of this group.
     */
    public WorkType getWorkType() {
        return this.workType;
    }

    public void setWorkType(WorkType workType) {
        this.workType = workType;
    }

    /**
     * The work status of this group.
     */
    public Work.Status getStatus() {
        return this.status;
    }

    public void setStatus(Work.Status status) {
        this.status = status;
    }

    /**
     * The number of works in this group.
     */
    public int getNumWorks() {
        return this.numWorks;
    }

    public void setNumWorks(int numWorks) {
        this.numWorks = numWorks;
    }

    /**
     * The total number of labware required as specified in these works.
     */
    public int getTotalLabwareRequired() {
        return this.totalLabwareRequired;
    }

    public void setTotalLabwareRequired(int totalLabwareRequired) {
        this.totalLabwareRequired = totalLabwareRequired;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkSummaryGroup that = (WorkSummaryGroup) o;
        return (this.numWorks == that.numWorks
                && this.totalLabwareRequired == that.totalLabwareRequired
                && Objects.equals(this.workType, that.workType)
                && this.status == that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workType, status);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("workType", workType==null ? null : workType.getName())
                .add("status", status)
                .add("numWorks", numWorks)
                .add("totalLabwareRequired", totalLabwareRequired)
                .toString();
    }
}