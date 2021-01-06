package uk.ac.sanger.sccp.stan.request.confirm;

import com.google.common.base.MoreObjects;

import java.util.*;

/**
 * Request to confirm the operations prelabelled for a collection of labware
 * @author dr6
 */
public class ConfirmOperationRequest {
    private List<ConfirmOperationLabware> labware;

    public ConfirmOperationRequest() {
        this(null);
    }

    public ConfirmOperationRequest(Collection<ConfirmOperationLabware> labware) {
        setLabware(labware);
    }

    public List<ConfirmOperationLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(Collection<ConfirmOperationLabware> labware) {
        this.labware = (labware==null ? new ArrayList<>() : new ArrayList<>(labware));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("labware", labware)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmOperationRequest that = (ConfirmOperationRequest) o;
        return Objects.equals(this.labware, that.labware);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labware);
    }
}
