package uk.ac.sanger.sccp.stan.request.confirm;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.Operation;

import java.util.*;

/**
 * The results to a successful confirm operation request
 * @author dr6
 */
public class ConfirmOperationResult {
    private List<Operation> operations;
    private List<Labware> labware;

    public ConfirmOperationResult() {
        this(null, null);
    }

    public ConfirmOperationResult(Collection<Operation> operations, Collection<Labware> labware) {
        setOperations(operations);
        setLabware(labware);
    }

    public List<Operation> getOperations() {
        return this.operations;
    }

    public void setOperations(Collection<Operation> operations) {
        this.operations = (operations==null ? new ArrayList<>() : new ArrayList<>(operations));
    }

    public List<Labware> getLabware() {
        return this.labware;
    }

    public void setLabware(Collection<Labware> labware) {
        this.labware = (labware==null ? new ArrayList<>() : new ArrayList<>(labware));
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
        ConfirmOperationResult that = (ConfirmOperationResult) o;
        return (Objects.equals(this.operations, that.operations)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operations, labware);
    }
}
