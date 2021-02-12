package uk.ac.sanger.sccp.stan.request;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.Operation;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * The results of a successful request to record operations.
 * @author dr6
 */
public class OperationResult {
    private List<Operation> operations;
    private List<Labware> labware;

    public OperationResult() {
        this(null, null);
    }

    public OperationResult(Iterable<Operation> operations, Iterable<Labware> labware) {
        setOperations(operations);
        setLabware(labware);
    }

    public List<Operation> getOperations() {
        return this.operations;
    }

    public void setOperations(Iterable<Operation> operations) {
        this.operations = newArrayList(operations);
    }

    public List<Labware> getLabware() {
        return this.labware;
    }

    public void setLabware(Iterable<Labware> labware) {
        this.labware = newArrayList(labware);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("operations", operations)
                .add("labware", labware)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationResult that = (OperationResult) o;
        return (Objects.equals(this.operations, that.operations)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operations, labware);
    }
}
