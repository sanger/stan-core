package uk.ac.sanger.sccp.stan.request.plan;

import uk.ac.sanger.sccp.stan.model.SlideCosting;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

/**
 * A request to create a new piece of labware
 * @author dr6
 */
public class PlanRequestLabware {
    private String labwareType;
    private String barcode;
    private String lotNumber;
    private SlideCosting costing;
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

    public String getLotNumber() {
        return this.lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public SlideCosting getCosting() {
        return this.costing;
    }

    public void setCosting(SlideCosting costing) {
        this.costing = costing;
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
                && Objects.equals(this.lotNumber, that.lotNumber)
                && this.costing==that.costing
                && Objects.equals(this.actions, that.actions));
    }

    @Override
    public int hashCode() {
        return Objects.hash(labwareType, barcode, lotNumber, costing, actions);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("labwareType", labwareType)
                .add("barcode", barcode)
                .add("lotNumber", lotNumber)
                .add("actions", actions)
                .add("costing", costing)
                .reprStringValues()
                .toString();
    }
}
