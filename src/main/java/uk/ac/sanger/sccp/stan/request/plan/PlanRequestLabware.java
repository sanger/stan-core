package uk.ac.sanger.sccp.stan.request.plan;

import com.google.common.base.MoreObjects;

import java.util.*;

/**
 * A request to create a new piece of labware
 * @author dr6
 */
public class PlanRequestLabware {
    private String labwareType;
    private String barcode;
    private List<PlanRequestAction> actions;

    public PlanRequestLabware() {
        this(null, null, null);
    }

    public PlanRequestLabware(String labwareType, String barcode, List<PlanRequestAction> actions) {
        this.labwareType = labwareType;
        this.barcode = barcode;
        setActions(actions);
    }

    public String getLabwareType() {
        return this.labwareType;
    }

    public void setLabwareType(String labwareType) {
        this.labwareType = labwareType;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public List<PlanRequestAction> getActions() {
        return this.actions;
    }

    public void setActions(List<PlanRequestAction> actions) {
        this.actions = (actions==null ? new ArrayList<>() : new ArrayList<>(actions));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanRequestLabware that = (PlanRequestLabware) o;
        return (Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.actions, that.actions));
    }

    @Override
    public int hashCode() {
        return Objects.hash(labwareType, barcode, actions);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("labwareType", labwareType)
                .add("barcode", barcode)
                .add("actions", actions)
                .toString();
    }
}
