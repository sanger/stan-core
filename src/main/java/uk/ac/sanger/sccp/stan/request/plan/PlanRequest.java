package uk.ac.sanger.sccp.stan.request.plan;

import com.google.common.base.MoreObjects;

import java.util.*;

/**
 * An operation plan request (i.e. a prelabel request)
 * @author dr6
 */
public class PlanRequest {
    private String operationType;
    private List<PlanRequestLabware> labware;

    public PlanRequest() {
        this(null, null);
    }

    public PlanRequest(String operationType, List<PlanRequestLabware> labware) {
        setOperationType(operationType);
        setLabware(labware);
    }

    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public List<PlanRequestLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(Collection<PlanRequestLabware> labware) {
        if (labware==null) {
            this.labware = new ArrayList<>();
        } else {
            this.labware = new ArrayList<>(labware);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanRequest that = (PlanRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, labware);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("operationType", operationType)
                .add("Labwares", labware)
                .toString();
    }
}
