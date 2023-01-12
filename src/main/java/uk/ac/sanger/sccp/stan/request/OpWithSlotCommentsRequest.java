package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * Request to record an operation on multiple labware in-place with comments on some slots.
 * @author dr6
 */
public class OpWithSlotCommentsRequest {
    private String operationType;
    private String workNumber;
    private List<LabwareWithSlotCommentsRequest> labware = List.of();

    public OpWithSlotCommentsRequest() {}

    public OpWithSlotCommentsRequest(String opType, String workNumber,
                                     List<LabwareWithSlotCommentsRequest> labware) {
        setOperationType(opType);
        setWorkNumber(workNumber);
        setLabware(labware);
    }

    /**
     * The name of the operation type to record.
     */
    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * The work number.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /**
     * The details of the labware.
     */
    public List<LabwareWithSlotCommentsRequest> getLabware() {
        return this.labware;
    }

    public void setLabware(List<LabwareWithSlotCommentsRequest> labware) {
        this.labware = (labware==null ? List.of() : labware);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpWithSlotCommentsRequest that = (OpWithSlotCommentsRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, workNumber, labware);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("OpWithSlotCommentsRequest")
                .add("operationType", operationType)
                .add("workNumber", workNumber)
                .add("labware", labware)
                .reprStringValues()
                .toString();
    }

    /**
     * Specification of comments in slots of a piece of labware.
     * @author dr6
     */
    public static class LabwareWithSlotCommentsRequest {
        private String barcode;
        private List<AddressCommentId> addressComments = List.of();

        public LabwareWithSlotCommentsRequest() {}

        public LabwareWithSlotCommentsRequest(String barcode, List<AddressCommentId> addressComments) {
            setBarcode(barcode);
            setAddressComments(addressComments);
        }

        /**
         * The barcode of the labware.
         */
        public String getBarcode() {
            return this.barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        /**
         * The comments in slots of this labware.
         */
        public List<AddressCommentId> getAddressComments() {
            return this.addressComments;
        }

        public void setAddressComments(List<AddressCommentId> addressComments) {
            this.addressComments = (addressComments==null ? List.of() : addressComments);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LabwareWithSlotCommentsRequest that = (LabwareWithSlotCommentsRequest) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.addressComments, that.addressComments));
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, addressComments);
        }

        @Override
        public String toString() {
            return BasicUtils.describe("LabwareWithSlotCommentsRequest")
                    .add("barcode", barcode)
                    .add("addressComments", addressComments)
                    .reprStringValues()
                    .toString();
        }
    }
}