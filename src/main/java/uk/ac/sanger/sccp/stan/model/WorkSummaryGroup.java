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
    private int totalNumBlocks, totalNumSlides, totalNumOriginalSamples;

    public WorkSummaryGroup(WorkType workType, Work.Status status, int numWorks,
                            int totalNumBlocks, int totalNumSlides, int totalNumOriginalSamples) {
        this.workType = workType;
        this.status = status;
        this.numWorks = numWorks;
        this.totalNumBlocks = totalNumBlocks;
        this.totalNumSlides = totalNumSlides;
        this.totalNumOriginalSamples = totalNumOriginalSamples;
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
     * The total number of blocks required as specified in these works.
     */
    public int getTotalNumBlocks() {
        return this.totalNumBlocks;
    }

    public void setTotalNumBlocks(int totalNumBlocks) {
        this.totalNumBlocks = totalNumBlocks;
    }

    /**
     * The total number of slides required as specified in these works.
     */
    public int getTotalNumSlides() {
        return this.totalNumSlides;
    }

    public void setTotalNumSlides(int totalNumSlides) {
        this.totalNumSlides = totalNumSlides;
    }

    /**
     * The total number of original samples required as specified in these works.
     */
    public int getTotalNumOriginalSamples() {
        return this.totalNumOriginalSamples;
    }

    public void setTotalNumOriginalSamples(int totalNumOriginalSamples) {
        this.totalNumOriginalSamples = totalNumOriginalSamples;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkSummaryGroup that = (WorkSummaryGroup) o;
        return (this.numWorks == that.numWorks
                && this.totalNumBlocks == that.totalNumBlocks
                && this.totalNumSlides == that.totalNumSlides
                && this.totalNumOriginalSamples == that.totalNumOriginalSamples
                && Objects.equals(this.workType, that.workType)
                && this.status == that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workType, status);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("WorkSummaryGroup")
                .add("workType", workType)
                .add("status", status)
                .add("numWorks", numWorks)
                .add("totalNumBlocks", totalNumBlocks)
                .add("totalNumSlides", totalNumSlides)
                .add("totalNumOriginalSamples", totalNumOriginalSamples)
                .toString();
    }
}