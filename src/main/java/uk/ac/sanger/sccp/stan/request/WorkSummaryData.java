package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.WorkSummaryGroup;
import uk.ac.sanger.sccp.stan.model.WorkType;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * The data required for a workSummary
 * @author bt8
 */
public class WorkSummaryData {
    private List<WorkType> workTypes;
    private List<WorkSummaryGroup> workSummaryGroups;

    public WorkSummaryData(List<WorkType> workTypes, List<WorkSummaryGroup> workSummaryGroups) {
        this.workTypes = workTypes;
        this.workSummaryGroups = workSummaryGroups;
    }

    public List<WorkType> getWorkTypes() {
        return workTypes;
    }

    public void setWorkTypes(List<WorkType> workTypes) {
        this.workTypes = workTypes;
    }

    public List<WorkSummaryGroup> getWorkSummaryGroups() {
        return workSummaryGroups;
    }

    public void setWorkSummaryGroups(List<WorkSummaryGroup> workSummaryGroups) {
        this.workSummaryGroups = workSummaryGroups;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkSummaryData that = (WorkSummaryData) o;
        return Objects.equals(workTypes, that.workTypes) && Objects.equals(workSummaryGroups, that.workSummaryGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workTypes, workSummaryGroups);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("WorkSummaryData")
                .add("workTypes", workTypes)
                .add("workSummaryGroups", workSummaryGroups)
                .toString();
    }
}
