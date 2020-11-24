package uk.ac.sanger.sccp.stan.request.plan;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.PlanOperation;

import java.util.*;

/**
 * @author dr6
 */
public class PlanResult {
    private List<PlanOperation> operations;
    private List<Labware> labware;

    public PlanResult() {
        this(null, null);
    }

    public PlanResult(List<PlanOperation> operations, List<Labware> labware) {
        setOperations(operations);
        setLabware(labware);
    }

    public List<PlanOperation> getOperations() {
        return this.operations;
    }

    public void setOperations(List<PlanOperation> operations) {
        this.operations = (operations==null ? new ArrayList<>() : new ArrayList<>(operations));
    }

    public List<Labware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<Labware> labware) {
        this.labware = (labware==null ? new ArrayList<>() : new ArrayList<>(labware));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanResult that = (PlanResult) o;
        return (Objects.equals(this.operations, that.operations)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operations, labware);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("operations", operations)
                .add("labware", labware)
                .toString();
    }
}
