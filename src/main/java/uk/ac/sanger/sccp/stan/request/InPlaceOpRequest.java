package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * A request to record an operation in place
 * @author dr6
 */
public class InPlaceOpRequest {
    private String operationType;
    private List<String> barcodes;
    private Integer equipmentId;
    private String workNumber;

    public InPlaceOpRequest() {}

    public InPlaceOpRequest(String operationType, List<String> barcodes, Integer equipmentId, String workNumber) {
        this.operationType = operationType;
        this.barcodes = barcodes;
        this.equipmentId = equipmentId;
        this.workNumber = workNumber;
    }

    public InPlaceOpRequest(String operationType, List<String> barcodes) {
        this(operationType, barcodes, null, null);
    }

    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public List<String> getBarcodes() {
        return this.barcodes;
    }

    public void setBarcodes(List<String> barcodes) {
        this.barcodes = barcodes;
    }

    public Integer getEquipmentId() {
        return this.equipmentId;
    }

    public void setEquipmentId(Integer equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InPlaceOpRequest that = (InPlaceOpRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.barcodes, that.barcodes)
                && Objects.equals(this.equipmentId, that.equipmentId)
                && Objects.equals(this.workNumber, that.workNumber));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, barcodes, equipmentId, workNumber);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .addRepr("operationType", operationType)
                .add("barcodes", barcodes)
                .addIfNotNull("equipmentId", equipmentId)
                .addIfNotNull("workNumber", workNumber)
                .toString();
    }
}
