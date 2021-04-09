package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * A request to copy slots from existing labware into a new plate.
 * @author dr6
 */
public class SlotCopyRequest {
    private String operationType;
    private String labwareType;
    private List<SlotCopyContent> contents = List.of();

    public SlotCopyRequest() {}

    public SlotCopyRequest(String operationType, String labwareType, List<SlotCopyContent> contents) {
        this.operationType = operationType;
        this.labwareType = labwareType;
        setContents(contents);
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public void setLabwareType(String labwareType) {
        this.labwareType = labwareType;
    }

    public void setContents(List<SlotCopyContent> contents) {
        this.contents = (contents==null ? List.of() : contents);
    }

    /** The name of the type of operation to record */
    public String getOperationType() {
        return this.operationType;
    }

    /** The name of the type of labware to create for the destination */
    public String getLabwareType() {
        return this.labwareType;
    }

    /** The description of what slots are being transferred into slots in the new labware */
    public List<SlotCopyContent> getContents() {
        return this.contents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotCopyRequest that = (SlotCopyRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.contents, that.contents));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, labwareType, contents);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("SlotCopyRequest")
                .add("operationType", operationType)
                .add("labwareType", labwareType)
                .add("contents", contents)
                .reprStringValues()
                .toString();
    }

    /**
     * An instruction to copy the contents of one source slot into one slot in the new labware.
     */
    public static class SlotCopyContent {
        private String sourceBarcode;
        private Address sourceAddress;
        private Address destinationAddress;

        public SlotCopyContent() {}

        public SlotCopyContent(String sourceBarcode, Address sourceAddress, Address destinationAddress) {
            this.sourceBarcode = sourceBarcode;
            this.sourceAddress = sourceAddress;
            this.destinationAddress = destinationAddress;
        }

        /** The barcode of the source labware */
        public String getSourceBarcode() {
            return this.sourceBarcode;
        }

        public void setSourceBarcode(String sourceBarcode) {
            this.sourceBarcode = sourceBarcode;
        }

        /** The address in the source labware */
        public Address getSourceAddress() {
            return this.sourceAddress;
        }

        public void setSourceAddress(Address sourceAddress) {
            this.sourceAddress = sourceAddress;
        }

        /** The address in the new labware */
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
            SlotCopyContent that = (SlotCopyContent) o;
            return (Objects.equals(this.sourceBarcode, that.sourceBarcode)
                    && Objects.equals(this.sourceAddress, that.sourceAddress)
                    && Objects.equals(this.destinationAddress, that.destinationAddress));
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceBarcode, sourceAddress, destinationAddress);
        }

        @Override
        public String toString() {
            return BasicUtils.describe("SlotCopyContent")
                    .add("sourceBarcode", sourceBarcode)
                    .add("sourceAddress", sourceAddress)
                    .add("destinationAddress", destinationAddress)
                    .reprStringValues()
                    .toString();
        }
    }
}
