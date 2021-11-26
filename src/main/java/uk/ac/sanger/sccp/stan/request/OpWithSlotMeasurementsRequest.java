package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * Request to record an operation with some measurements in slots
 * @author dr6
 */
public class OpWithSlotMeasurementsRequest {
    private String barcode;
    private String operationType;
    private String workNumber;
    private List<SlotMeasurementRequest> slotMeasurements;

    public OpWithSlotMeasurementsRequest() {
        this(null, null, null, null);
    }

    public OpWithSlotMeasurementsRequest(String barcode, String operationType, String workNumber,
                                         List<SlotMeasurementRequest> slotMeasurements) {
        this.barcode = barcode;
        this.operationType = operationType;
        this.workNumber = workNumber;
        this.slotMeasurements = slotMeasurements;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    public List<SlotMeasurementRequest> getSlotMeasurements() {
        return this.slotMeasurements;
    }

    public void setSlotMeasurements(List<SlotMeasurementRequest> slotMeasurements) {
        this.slotMeasurements = (slotMeasurements==null ? List.of() : slotMeasurements);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpWithSlotMeasurementsRequest that = (OpWithSlotMeasurementsRequest) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.slotMeasurements, that.slotMeasurements));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, operationType, workNumber, slotMeasurements);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("OpWithSlotMeasurementsRequest")
                .addRepr("barcode", barcode)
                .addRepr("operationType", operationType)
                .addRepr("workNumber", workNumber)
                .add("slotMeasurements", slotMeasurements)
                .toString();
    }
}
