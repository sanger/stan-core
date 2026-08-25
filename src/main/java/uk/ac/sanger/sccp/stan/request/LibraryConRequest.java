package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * A request to record dual index and amplification ops.
 * @author dr6
 */
public class LibraryConRequest {
    private String workNumber;
    private String labwareBarcode;
    private List<ReagentTransfer> reagentTransfers;
    private String reagentPlateType;
    private List<SlotMeasurementRequest> slotMeasurements;

    /** The work number to associate with these operations. */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /** The barcode of the labware */
    public String getLabwareBarcode() {
        return this.labwareBarcode;
    }

    public void setLabwareBarcode(String labwareBarcode) {
        this.labwareBarcode = labwareBarcode;
    }

    /** The transfers from aliquot slots to destination slots. */
    public List<ReagentTransfer> getReagentTransfers() {
        return this.reagentTransfers;
    }

    public void setReagentTransfers(List<ReagentTransfer> reagentTransfers) {
        this.reagentTransfers = reagentTransfers;
    }

    /** The type of reagent plate involved. */
    public String getReagentPlateType() {
        return this.reagentPlateType;
    }

    public void setReagentPlateType(String reagentPlateType) {
        this.reagentPlateType = reagentPlateType;
    }

    /** The measurement to record on slots in the destination. */
    public List<SlotMeasurementRequest> getSlotMeasurements() {
        return this.slotMeasurements;
    }

    public void setSlotMeasurements(List<SlotMeasurementRequest> slotMeasurements) {
        this.slotMeasurements = slotMeasurements;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LibraryConRequest that = (LibraryConRequest) o;
        return (Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.labwareBarcode, that.labwareBarcode)
                && Objects.equals(this.reagentTransfers, that.reagentTransfers)
                && Objects.equals(this.reagentPlateType, that.reagentPlateType)
                && Objects.equals(this.slotMeasurements, that.slotMeasurements));
    }

    @Override
    public int hashCode() {
        return Objects.hash(workNumber, labwareBarcode, reagentTransfers, reagentPlateType, slotMeasurements);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("workNumber", workNumber)
                .add("labwareBarcode", labwareBarcode)
                .add("reagentTransfers", reagentTransfers)
                .add("reagentPlateType", reagentPlateType)
                .add("slotMeasurements", slotMeasurements)
                .reprStringValues()
                .toString();
    }
}