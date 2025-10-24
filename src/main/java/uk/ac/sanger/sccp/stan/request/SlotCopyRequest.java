package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A request to copy slots from existing labware into new or prebarcoded pieces of labware.
 * @author dr6
 */
public class SlotCopyRequest {
    private String operationType;
    private String workNumber;
    private ExecutionType executionType;
    private List<SlotCopySource> sources;
    private List<SlotCopyDestination> destinations;

    public SlotCopyRequest() {
        this(null, null, (ExecutionType) null, null, null);
    }

    public SlotCopyRequest(String operationType, String workNumber, ExecutionType executionType, List<SlotCopySource> sources,
                           List<SlotCopyDestination> destinations) {
        this.operationType = operationType;
        this.workNumber = workNumber;
        this.executionType = executionType;
        setSources(sources);
        setDestinations(destinations);
    }

    public SlotCopyRequest(String operationType, String labwareTypeName, List<SlotCopyContent> contents, String workNumber,
                           String preBarcode) {
        this(operationType, workNumber, null, null, List.of(new SlotCopyDestination(labwareTypeName, preBarcode,
                null, null, null, contents, null, null)));
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    public void setSources(List<SlotCopySource> sources) {
        this.sources = (sources==null ? List.of() : sources);
    }

    public void setDestinations(List<SlotCopyDestination> destinations) {
        this.destinations = (destinations==null ? List.of() : destinations);
    }

    /** The name of the type of operation to record */
    public String getOperationType() {
        return this.operationType;
    }

    /** An optional work number to associate with this operation. */
    public String getWorkNumber() {
        return this.workNumber;
    }

    /** Whether the operation was automated or manual  */
    public ExecutionType getExecutionType() {
        return this.executionType;
    }

    public void setExecutionType(ExecutionType executionType) {
        this.executionType = executionType;
    }

    /** The source labware and their new labware states (if specified). */
    public List<SlotCopySource> getSources() {
        return this.sources;
    }

    /* The destination labware and its contents. */
    public List<SlotCopyDestination> getDestinations() {
        return this.destinations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotCopyRequest that = (SlotCopyRequest) o;
        return (this.executionType==that.executionType
                && Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.sources, that.sources)
                && Objects.equals(this.destinations, that.destinations)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, workNumber, sources, destinations);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("SlotCopyRequest")
                .add("operationType", operationType)
                .add("workNumber", workNumber)
                .add("executionType", executionType)
                .add("sources", sources)
                .add("destinations", destinations)
                .reprStringValues()
                .toString();
    }

    /**
     * A source for slot copy, if a new labware state is specified.
     * @author dr6
     */
    public static class SlotCopySource {
        private String barcode;
        private Labware.State labwareState;

        public SlotCopySource() {}

        public SlotCopySource(String barcode, Labware.State labwareState) {
            this.barcode = barcode;
            this.labwareState = labwareState;
        }

        /**
         * The barcode of the source.
         */
        public String getBarcode() {
            return this.barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        /**
         * The new labware state of the source.
         */
        public Labware.State getLabwareState() {
            return this.labwareState;
        }

        public void setLabwareState(Labware.State labwareState) {
            this.labwareState = labwareState;
        }

        @Override
        public String toString() {
            return repr(barcode)+": "+labwareState;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SlotCopySource that = (SlotCopySource) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && this.labwareState == that.labwareState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, labwareState);
        }
    }

    /**
     * A destination for slot copy.
     * @author dr6
     */
    public static class SlotCopyDestination {
        private String labwareType;
        private String barcode;
        private String bioState;
        private SlideCosting costing;
        private String lotNumber;
        private String probeLotNumber;
        private String preBarcode;
        private String lpNumber;
        private String reagentLot;
        private String reagentALot;
        private String reagentBLot;
        private SlideCosting reagentCosting;
        private List<SlotCopyContent> contents;

        public SlotCopyDestination() {
            this(null, null, null, null, null, null, null, null);
        }

        public SlotCopyDestination(String labwareTypeName, String preBarcode, SlideCosting costing,
                                   String lotNumber, String probeLotNumber, List<SlotCopyContent> contents,
                                   String bioState, String lpNumber) {
            this.labwareType = labwareTypeName;
            this.preBarcode = preBarcode;
            this.costing = costing;
            this.lotNumber = lotNumber;
            this.bioState = bioState;
            this.probeLotNumber = probeLotNumber;
            this.lpNumber = lpNumber;
            setContents(contents);
        }

        /**
         * The name of the type of the new destination labware.
         */
        public String getLabwareType() {
            return this.labwareType;
        }

        public void setLabwareType(String labwareType) {
            this.labwareType = labwareType;
        }

        /**
         * The barcode of an existing (active) destination labware to add further samples into.
         */
        public String getBarcode() {
            return this.barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        /**
         * The bio state for samples in the destination (if specified).
         */
        public String getBioState() {
            return this.bioState;
        }

        public void setBioState(String bioState) {
            this.bioState = bioState;
        }

        /**
         * The costing of the slide, if specified.
         */
        public SlideCosting getCosting() {
            return this.costing;
        }

        public void setCosting(SlideCosting costing) {
            this.costing = costing;
        }

        /**
         * The lot number for the slide, if specified.
         */
        public String getLotNumber() {
            return this.lotNumber;
        }

        public void setLotNumber(String lotNumber) {
            this.lotNumber = lotNumber;
        }

        /**
         * The probe lot number, if specified.
         */
        public String getProbeLotNumber() {
            return this.probeLotNumber;
        }

        public void setProbeLotNumber(String probeLotNumber) {
            this.probeLotNumber = probeLotNumber;
        }

        /**
         * The barcode of the new labware, if it is prebarcoded.
         */
        public String getPreBarcode() {
            return this.preBarcode;
        }

        public void setPreBarcode(String preBarcode) {
            this.preBarcode = preBarcode;
        }

        /** The LP number of this labware. **/
        public String getLpNumber() {
            return this.lpNumber;
        }

        public void setLpNumber(String lpNumber) {
            this.lpNumber = lpNumber;
        }

        public String getReagentALot() {
            return this.reagentALot;
        }

        public void setReagentALot(String reagentALot) {
            this.reagentALot = reagentALot;
        }

        public String getReagentBLot() {
            return this.reagentBLot;
        }

        public void setReagentBLot(String reagentBLot) {
            this.reagentBLot = reagentBLot;
        }

        /**
         * The specifications of which source slots are being copied into what addresses in the destination labware.
         */
        public List<SlotCopyContent> getContents() {
            return this.contents;
        }

        public void setContents(List<SlotCopyContent> contents) {
            this.contents = (contents==null ? List.of() : contents);
        }

        public String getReagentLot() {
            return this.reagentLot;
        }

        public void setReagentLot(String reagentLot) {
            this.reagentLot = reagentLot;
        }

        public SlideCosting getReagentCosting() {
            return this.reagentCosting;
        }

        public void setReagentCosting(SlideCosting reagentCosting) {
            this.reagentCosting = reagentCosting;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SlotCopyDestination that = (SlotCopyDestination) o;
            return (Objects.equals(this.labwareType, that.labwareType)
                    && Objects.equals(this.bioState, that.bioState)
                    && this.costing == that.costing
                    && Objects.equals(this.lotNumber, that.lotNumber)
                    && Objects.equals(this.probeLotNumber, that.probeLotNumber)
                    && Objects.equals(this.preBarcode, that.preBarcode)
                    && Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.lpNumber, that.lpNumber)
                    && Objects.equals(this.contents, that.contents)
                    && Objects.equals(this.reagentALot, that.reagentALot)
                    && Objects.equals(this.reagentBLot, that.reagentBLot)
                    && Objects.equals(this.reagentLot, that.reagentLot)
                    && this.reagentCosting == that.reagentCosting
            );
        }

        @Override
        public int hashCode() {
            return (barcode==null ? 0 : barcode.hashCode());
        }

        @Override
        public String toString() {
            return describe(this)
                    .add("labwareType", labwareType)
                    .add("barcode", barcode)
                    .add("bioState", bioState)
                    .add("costing", costing)
                    .add("lotNumber", lotNumber)
                    .add("probeLotNumber", probeLotNumber)
                    .add("preBarcode", preBarcode)
                    .add("lpNumber", lpNumber)
                    .add("contents", contents)
                    .add("reagentLot", reagentLot)
                    .add("reagentALot", reagentALot)
                    .add("reagentBLot", reagentBLot)
                    .add("reagentCosting", reagentCosting)
                    .reprStringValues()
                    .toString();
        }
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
            setSourceBarcode(sourceBarcode);
            setSourceAddress(sourceAddress);
            setDestinationAddress(destinationAddress);
        }

        /** The barcode of the source labware */
        public String getSourceBarcode() {
            return this.sourceBarcode;
        }

        public void setSourceBarcode(String sourceBarcode) {
            this.sourceBarcode = (sourceBarcode==null ? null : sourceBarcode.toUpperCase());
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
            return String.format("{sourceBarcode=%s, sourceAddress=%s, destinationAddress=%s}",
                    repr(sourceBarcode), sourceAddress, destinationAddress);
        }
    }
}
