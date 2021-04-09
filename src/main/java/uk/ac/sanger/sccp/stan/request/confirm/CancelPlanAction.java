package uk.ac.sanger.sccp.stan.request.confirm;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.PlanAction;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * Part of a confirm request indicating that a particular plan action is cancelled.
 * @author dr6
 */
public class CancelPlanAction {
    private Address destinationAddress;
    private Integer sampleId;
    private Integer newSection;

    public CancelPlanAction() {}

    public CancelPlanAction(Address destinationAddress, Integer sampleId, Integer newSection) {
        this.destinationAddress = destinationAddress;
        this.sampleId = sampleId;
        this.newSection = newSection;
    }

    public CancelPlanAction(PlanAction planAction) {
        this(planAction.getDestination().getAddress(), planAction.getSample().getId(), planAction.getNewSection());
    }

    public Address getDestinationAddress() {
        return this.destinationAddress;
    }

    public void setDestinationAddress(Address destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public Integer getNewSection() {
        return this.newSection;
    }

    public void setNewSection(Integer newSection) {
        this.newSection = newSection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CancelPlanAction that = (CancelPlanAction) o;
        return (Objects.equals(this.destinationAddress, that.destinationAddress)
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.newSection, that.newSection));
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinationAddress, sampleId, newSection);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("CancelPlanAction")
                .add("destinationAddress", destinationAddress)
                .add("sampleId", sampleId)
                .add("newSection", newSection)
                .toString();
    }

    public String describeWithBarcode(String barcode) {
        return String.format("(barcode=%s, address=%s, sampleId=%s, newSection=%s)",
                barcode, destinationAddress, sampleId, newSection);
    }

    public static CancelPlanAction forPlanAction(PlanAction planAction) {
        return new CancelPlanAction(planAction.getDestination().getAddress(), planAction.getSample().getId(),
                planAction.getNewSection());
    }
}
