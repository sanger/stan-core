package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * A request to transfer material from one source labware into multiple new destination labware (first slot).
 * @author dr6
 */
public class AliquotRequest {
    private String operationType;
    private String barcode;
    private String labwareType;
    private Integer numLabware;
    private String workNumber;

    public AliquotRequest() {}

    public AliquotRequest(String operationType, String barcode, String labwareType, Integer numLabware,
                          String workNumber) {
        this.operationType = operationType;
        this.barcode = barcode;
        this.labwareType = labwareType;
        this.numLabware = numLabware;
        this.workNumber = workNumber;
    }

    /**
     * The name of the operation to record.
     */
    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * The barcode of the source labware.
     */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /**
     * The name of the labware type for the destination labware.
     */
    public String getLabwareType() {
        return this.labwareType;
    }

    public void setLabwareType(String labwareType) {
        this.labwareType = labwareType;
    }

    /**
     * The number of destination labware to create.
     */
    public Integer getNumLabware() {
        return this.numLabware;
    }

    public void setNumLabware(Integer numLabware) {
        this.numLabware = numLabware;
    }

    /**
     * An optional work number to associate with this operation.
     */
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
        AliquotRequest that = (AliquotRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.numLabware, that.numLabware)
                && Objects.equals(this.workNumber, that.workNumber));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, barcode, labwareType, numLabware, workNumber);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("AliquotRequest")
                .add("operationType", operationType)
                .add("barcode", barcode)
                .add("labwareType", labwareType)
                .add("numLabware", numLabware)
                .add("workNumber", workNumber)
                .reprStringValues()
                .toString();
    }
}