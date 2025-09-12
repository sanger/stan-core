package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.ExecutionType;
import uk.ac.sanger.sccp.stan.model.SlideCosting;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyContent;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopySource;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * Saved data for an incomplete slot copy operation
 * @author dr6
 */
public class SlotCopySave {
    private List<SlotCopySource> sources = List.of();
    private String operationType;
    private String workNumber;
    private String lpNumber;
    private ExecutionType executionType;
    private String labwareType;
    private String barcode;
    private String bioState;
    private SlideCosting costing;
    private String lotNumber;
    private String reagentALot;
    private String reagentBLot;
    private String probeLotNumber;
    private String preBarcode;
    private List<SlotCopyContent> contents = List.of();

    /** The source labware and their new labware states (if specified). */
    public List<SlotCopySource> getSources() {
        return this.sources;
    }

    public void setSources(List<SlotCopySource> sources) {
        this.sources = nullToEmpty(sources);
    }

    /** The name of the type of operation being recorded to describe the contents being copied. */
    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /** An optional work number to associate with this operation. */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /** The LP number of the new labware, required. */
    public String getLpNumber() {
        return this.lpNumber;
    }

    public void setLpNumber(String lpNumber) {
        this.lpNumber = lpNumber;
    }

    /** Whether the execution was automated or manual. */
    public ExecutionType getExecutionType() {
        return this.executionType;
    }

    public void setExecutionType(ExecutionType executionType) {
        this.executionType = executionType;
    }

    /** The name of the type of the destination labware (if it is new labware). */
    public String getLabwareType() {
        return this.labwareType;
    }

    public void setLabwareType(String labwareType) {
        this.labwareType = labwareType;
    }

    /** The barcode of the existing piece of labware. */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /** The bio state for samples in the destination (if specified). */
    public String getBioState() {
        return this.bioState;
    }

    public void setBioState(String bioState) {
        this.bioState = bioState;
    }

    /** The costing of the slide, if specified. */
    public SlideCosting getCosting() {
        return this.costing;
    }

    public void setCosting(SlideCosting costing) {
        this.costing = costing;
    }

    /** The lot number of the slide, if specified. */
    public String getLotNumber() {
        return this.lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    /** The probe lot number of the slide, if specified. */
    public String getProbeLotNumber() {
        return this.probeLotNumber;
    }

    public void setProbeLotNumber(String probeLotNumber) {
        this.probeLotNumber = probeLotNumber;
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

    /** The barcode of the new labware, if it is prebarcoded. */
    public String getPreBarcode() {
        return this.preBarcode;
    }

    public void setPreBarcode(String preBarcode) {
        this.preBarcode = preBarcode;
    }

    /** The specifications of which source slots are being copied into what addresses in the destination labware. */
    public List<SlotCopyContent> getContents() {
        return this.contents;
    }

    public void setContents(List<SlotCopyContent> contents) {
        this.contents = nullToEmpty(contents);
    }

    @Override
    public String toString() {
        return describe(this)
                .add("sources", sources)
                .add("operationType", operationType)
                .add("workNumber", workNumber)
                .add("lpNumber", lpNumber)
                .add("executionType", executionType)
                .add("labwareType", labwareType)
                .add("barcode", barcode)
                .add("bioState", bioState)
                .add("costing", costing)
                .add("lotNumber", lotNumber)
                .add("probeLotNumber", probeLotNumber)
                .add("reagentALot", reagentALot)
                .add("reagentBLot", reagentBLot)
                .add("preBarcode", preBarcode)
                .add("contents", contents)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != this.getClass()) return false;
        SlotCopySave that = (SlotCopySave) o;
        return (Objects.equals(this.sources, that.sources)
                && Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.lpNumber, that.lpNumber)
                && Objects.equals(this.executionType, that.executionType)
                && Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.bioState, that.bioState)
                && Objects.equals(this.costing, that.costing)
                && Objects.equals(this.lotNumber, that.lotNumber)
                && Objects.equals(this.probeLotNumber, that.probeLotNumber)
                && Objects.equals(this.reagentALot, that.reagentALot)
                && Objects.equals(this.reagentBLot, that.reagentBLot)
                && Objects.equals(this.preBarcode, that.preBarcode)
                && Objects.equals(this.contents, that.contents)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(workNumber, lpNumber);
    }
}