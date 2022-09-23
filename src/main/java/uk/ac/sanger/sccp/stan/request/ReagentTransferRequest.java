package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * A request to transfer reagents from reagent plates to a STAN labware.
 * @author dr6
 */
public class ReagentTransferRequest {
    private String operationType;
    private String workNumber;
    private String destinationBarcode;
    private List<ReagentTransfer> transfers;
    private String plateType;

    public ReagentTransferRequest() {
        this(null, null, null, null, null);
    }

    public ReagentTransferRequest(String operationType, String workNumber, String destinationBarcode,
                                  List<ReagentTransfer> transfers, String plateType) {
        this.operationType = operationType;
        this.workNumber = workNumber;
        this.destinationBarcode = destinationBarcode;
        setTransfers(transfers);
        this.plateType = plateType;
    }

    /**
     * The name of the operation being performed.
     */
    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * The work number to associate with the operation.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /**
     * The barcode of the destination labware.
     */
    public String getDestinationBarcode() {
        return this.destinationBarcode;
    }

    public void setDestinationBarcode(String destinationBarcode) {
        this.destinationBarcode = destinationBarcode;
    }

    /**
     * The transfers from aliquot slots to destination slots.
     */
    public List<ReagentTransfer> getTransfers() {
        return this.transfers;
    }

    public void setTransfers(List<ReagentTransfer> transfers) {
        this.transfers = (transfers==null ? List.of() : transfers);
    }

    public String getPlateType() {
        return this.plateType;
    }

    public void setPlateType(String plateType) {
        this.plateType = plateType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReagentTransferRequest that = (ReagentTransferRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.destinationBarcode, that.destinationBarcode)
                && Objects.equals(this.plateType, that.plateType)
                && Objects.equals(this.transfers, that.transfers));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, workNumber, destinationBarcode, plateType, transfers);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ReagentTransferRequest")
                .add("operationType", operationType)
                .add("workNumber", workNumber)
                .add("destinationBarcode", destinationBarcode)
                .add("plateType", plateType)
                .add("transfers", transfers)
                .reprStringValues()
                .toString();
    }

    /**
     * A specification that a particular reagent slot should be transferred to an address.
     */
    public static class ReagentTransfer {
        private String reagentPlateBarcode;
        private Address reagentSlotAddress;
        private Address destinationAddress;

        public ReagentTransfer() {}

        public ReagentTransfer(String reagentPlateBarcode, Address reagentSlotAddress, Address destinationAddress) {
            this.reagentPlateBarcode = reagentPlateBarcode;
            this.reagentSlotAddress = reagentSlotAddress;
            this.destinationAddress = destinationAddress;
        }

        /**
         * The barcode of a reagent plate.
         */
        public String getReagentPlateBarcode() {
            return this.reagentPlateBarcode;
        }

        public void setReagentPlateBarcode(String reagentPlateBarcode) {
            this.reagentPlateBarcode = reagentPlateBarcode;
        }

        /**
         * The address of a slot in the reagent plate.
         */
        public Address getReagentSlotAddress() {
            return this.reagentSlotAddress;
        }

        public void setReagentSlotAddress(Address reagentSlotAddress) {
            this.reagentSlotAddress = reagentSlotAddress;
        }

        /**
         * The address if a slot in the destination labware.
         */
        public Address getDestinationAddress() {
            return this.destinationAddress;
        }

        public void setDestinationAddress(Address destinationAddress) {
            this.destinationAddress = destinationAddress;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReagentTransfer that = (ReagentTransfer) o;
            return (Objects.equals(this.reagentPlateBarcode, that.reagentPlateBarcode)
                    && Objects.equals(this.reagentSlotAddress, that.reagentSlotAddress)
                    && Objects.equals(this.destinationAddress, that.destinationAddress));
        }

        @Override
        public int hashCode() {
            return Objects.hash(reagentPlateBarcode, reagentSlotAddress, destinationAddress);
        }

        @Override
        public String toString() {
            return BasicUtils.describe("ReagentTransfer")
                    .addRepr("reagentPlateBarcode", reagentPlateBarcode)
                    .add("reagentSlotAddress", reagentSlotAddress)
                    .add("destinationAddress", destinationAddress)
                    .toString();
        }
    }
}