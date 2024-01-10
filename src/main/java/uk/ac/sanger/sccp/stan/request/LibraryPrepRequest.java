package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyDestination;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopySource;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * A request to record transfer, dual index and amplification ops.
 * @author dr6
 */
public class LibraryPrepRequest {
    private String workNumber;
    private List<SlotCopySource> sources;
    private SlotCopyDestination destination;
    private List<ReagentTransfer> reagentTransfers;
    private String reagentPlateType;
    private List<SlotMeasurementRequest> slotMeasurements;

    /**
     * The work number to associate with these operations.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /**
     * The source labware for this request.
     */
    public List<SlotCopySource> getSources() {
        return this.sources;
    }

    public void setSources(List<SlotCopySource> sources) {
        this.sources = sources;
    }

    /**
     * The one destination labware for this request, and the description of what is transferred into it.
     */
    public SlotCopyDestination getDestination() {
        return this.destination;
    }

    public void setDestination(SlotCopyDestination destination) {
        this.destination = destination;
    }

    /**
     * The transfers from aliquot slots to destination slots.
     */
    public List<ReagentTransfer> getReagentTransfers() {
        return this.reagentTransfers;
    }

    public void setReagentTransfers(List<ReagentTransfer> reagentTransfers) {
        this.reagentTransfers = reagentTransfers;
    }

    /**
     * The type of reagent plate involved.
     */
    public String getReagentPlateType() {
        return this.reagentPlateType;
    }

    public void setReagentPlateType(String reagentPlateType) {
        this.reagentPlateType = reagentPlateType;
    }

    /**
     * The measurement to record on slots in the destination.
     */
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
        LibraryPrepRequest that = (LibraryPrepRequest) o;
        return (Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.sources, that.sources)
                && Objects.equals(this.destination, that.destination)
                && Objects.equals(this.reagentTransfers, that.reagentTransfers)
                && Objects.equals(this.reagentPlateType, that.reagentPlateType)
                && Objects.equals(this.slotMeasurements, that.slotMeasurements));
    }

    @Override
    public int hashCode() {
        return Objects.hash(workNumber, sources, destination, reagentTransfers, reagentPlateType, slotMeasurements);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("workNumber", workNumber)
                .add("sources", sources)
                .add("destination", destination)
                .add("reagentTransfers", reagentTransfers)
                .add("reagentPlateType", reagentPlateType)
                .add("slotMeasurements", slotMeasurements)
                .reprStringValues()
                .toString();
    }
}