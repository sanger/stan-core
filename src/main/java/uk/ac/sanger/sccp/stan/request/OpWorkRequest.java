package uk.ac.sanger.sccp.stan.request;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * A request to alter the work linked to existing operations.
 * @author dr6
 */
public class OpWorkRequest {
    private String workNumber;
    private List<Integer> opIds = List.of();

    public OpWorkRequest() {}

    public OpWorkRequest(String workNumber, List<Integer> opIds) {
        setWorkNumber(workNumber);
        setOpIds(opIds);
    }

    /** The work number to link. */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /** The operation ids to link to the work */
    public List<Integer> getOpIds() {
        return this.opIds;
    }

    public void setOpIds(List<Integer> opIds) {
        this.opIds = opIds;
    }

    @Override
    public String toString() {
        return describe(this)
                .add("workNumber", workNumber)
                .add("opIds", opIds)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != this.getClass()) return false;
        OpWorkRequest that = (OpWorkRequest) o;
        return (Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.opIds, that.opIds)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(workNumber, opIds);
    }
}