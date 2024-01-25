package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * A helper data type used to return information about releases to be put into a file
 * @author dr6
 */
public class ReleaseEntry {
    private final Labware labware;
    private final Slot slot;
    private final Sample sample;
    private Integer lastSection;
    private String sourceBarcode;
    private String sectionThickness;
    private Address sourceAddress;
    private String storageAddress;
    private String stainType;
    private String bondBarcode;
    private Integer coverage;
    private String reagentPlateType;
    private String reagentSource;
    private String cq;
    private String visiumConcentration;
    private String visiumConcentrationType;
    private Integer rnascopePlex;
    private Integer ihcPlex;
    private LocalDate sectionDate;
    private String permTime;
    private String samplePosition;
    private String sectionComment;
    private Map<String, String> tagData = Map.of();

    private LocalDateTime hybridStart, hybridEnd;
    private String xeniumPlex, xeniumProbe, xeniumProbeLot;
    private String hybridComment;

    private LocalDateTime xeniumStart, xeniumEnd;
    private String xeniumRoi, xeniumReagentALot, xeniumReagentBLot, xeniumCassettePosition, xeniumRun, xeniumComment;
    private String solution;
    private String stainQcComment;
    private String amplificationCycles;
    private String flagDescription = "";

    private String equipment;

    public ReleaseEntry(Labware labware, Slot slot, Sample sample) {
        this(labware, slot, sample, null);
    }

    public ReleaseEntry(Labware labware, Slot slot, Sample sample, String storageAddress) {
        this.labware = labware;
        this.slot = slot;
        this.sample = sample;
        this.storageAddress = storageAddress;
    }

    public Labware getLabware() {
        return this.labware;
    }

    public Slot getSlot() {
        return this.slot;
    }

    public Sample getSample() {
        return this.sample;
    }

    public Integer getLastSection() {
        return this.lastSection;
    }

    public void setLastSection(Integer lastSection) {
        this.lastSection = lastSection;
    }

    public void setSourceBarcode(String sourceBarcode) {
        this.sourceBarcode = sourceBarcode;
    }

    public String getSourceBarcode() {
        return this.sourceBarcode;
    }

    public String getSectionThickness() {
        return this.sectionThickness;
    }

    public void setSectionThickness(String sectionThickness) {
        this.sectionThickness = sectionThickness;
    }

    public Address getSourceAddress() {
        return this.sourceAddress;
    }

    public void setSourceAddress(Address sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public String getStorageAddress() {
        return this.storageAddress;
    }

    public void setStorageAddress(String storageAddress) {
        this.storageAddress = storageAddress;
    }

    public String getStainType() {
        return this.stainType;
    }

    public void setStainType(String stainType) {
        this.stainType = stainType;
    }

    public String getBondBarcode() {
        return this.bondBarcode;
    }

    public void setBondBarcode(String bondBarcode) {
        this.bondBarcode = bondBarcode;
    }

    public Integer getCoverage() {
        return this.coverage;
    }

    public void setCoverage(Integer coverage) {
        this.coverage = coverage;
    }

    public String getReagentPlateType() {
        return this.reagentPlateType;
    }

    public void setReagentPlateType(String reagentPlateType) {
        this.reagentPlateType = reagentPlateType;
    }

    public String getReagentSource() {
        return this.reagentSource;
    }

    public void setReagentSource(String reagentSource) {
        this.reagentSource = reagentSource;
    }

    public String getCq() {
        return this.cq;
    }

    public void setCq(String cq) {
        this.cq = cq;
    }

    public String getVisiumConcentration() {
        return this.visiumConcentration;
    }

    public void setVisiumConcentration(String visiumConcentration) {
        this.visiumConcentration = visiumConcentration;
    }

    public String getVisiumConcentrationType() {
        return this.visiumConcentrationType;
    }

    public void setVisiumConcentrationType(String visiumConcentrationType) {
        this.visiumConcentrationType = visiumConcentrationType;
    }

    public Integer getRnascopePlex() {
        return this.rnascopePlex;
    }

    public void setRnascopePlex(Integer rnascopePlex) {
        this.rnascopePlex = rnascopePlex;
    }

    public Integer getIhcPlex() {
        return this.ihcPlex;
    }

    public void setIhcPlex(Integer ihcPlex) {
        this.ihcPlex = ihcPlex;
    }

    public LocalDate getSectionDate() {
        return this.sectionDate;
    }

    public void setSectionDate(LocalDate sectionDate) {
        this.sectionDate = sectionDate;
    }

    public String getPermTime() {
        return this.permTime;
    }

    public void setPermTime(String permTime) {
        this.permTime = permTime;
    }

    /** The region of the sample inside the slot, if specified */
    public String getSamplePosition() {
        return this.samplePosition;
    }

    public void setSamplePosition(String samplePosition) {
        this.samplePosition = samplePosition;
    }

    /** The comment (if any) from sectioning this slot/sample */
    public String getSectionComment() {
        return this.sectionComment;
    }

    public void setSectionComment(String sectionComment) {
        this.sectionComment = sectionComment;
    }

    public Map<String, String> getTagData() {
        return this.tagData;
    }

    public void setTagData(Map<String, String> tagData) {
        this.tagData = nullToEmpty(tagData);
    }

    public LocalDateTime getHybridStart() {
        return this.hybridStart;
    }

    public void setHybridStart(LocalDateTime hybridStart) {
        this.hybridStart = hybridStart;
    }

    public LocalDateTime getHybridEnd() {
        return this.hybridEnd;
    }

    public void setHybridEnd(LocalDateTime hybridEnd) {
        this.hybridEnd = hybridEnd;
    }

    public String getXeniumPlex() {
        return this.xeniumPlex;
    }

    public void setXeniumPlex(String xeniumPlex) {
        this.xeniumPlex = xeniumPlex;
    }

    public String getXeniumProbe() {
        return this.xeniumProbe;
    }

    public void setXeniumProbe(String xeniumProbe) {
        this.xeniumProbe = xeniumProbe;
    }

    public String getXeniumProbeLot() {
        return this.xeniumProbeLot;
    }

    public void setXeniumProbeLot(String xeniumProbeLot) {
        this.xeniumProbeLot = xeniumProbeLot;
    }

    public String getHybridComment() {
        return this.hybridComment;
    }

    public void setHybridComment(String hybridComment) {
        this.hybridComment = hybridComment;
    }

    public LocalDateTime getXeniumStart() {
        return this.xeniumStart;
    }

    public void setXeniumStart(LocalDateTime xeniumStart) {
        this.xeniumStart = xeniumStart;
    }

    public LocalDateTime getXeniumEnd() {
        return this.xeniumEnd;
    }

    public void setXeniumEnd(LocalDateTime xeniumEnd) {
        this.xeniumEnd = xeniumEnd;
    }

    public String getXeniumRoi() {
        return this.xeniumRoi;
    }

    public void setXeniumRoi(String xeniumRoi) {
        this.xeniumRoi = xeniumRoi;
    }

    public String getXeniumReagentALot() {
        return this.xeniumReagentALot;
    }

    public void setXeniumReagentALot(String xeniumReagentALot) {
        this.xeniumReagentALot = xeniumReagentALot;
    }

    public String getXeniumReagentBLot() {
        return this.xeniumReagentBLot;
    }

    public void setXeniumReagentBLot(String xeniumReagentBLot) {
        this.xeniumReagentBLot = xeniumReagentBLot;
    }

    public String getXeniumCassettePosition() {
        return this.xeniumCassettePosition;
    }

    public void setXeniumCassettePosition(String xeniumCassettePosition) {
        this.xeniumCassettePosition = xeniumCassettePosition;
    }

    public String getXeniumRun() {
        return this.xeniumRun;
    }

    public void setXeniumRun(String xeniumRun) {
        this.xeniumRun = xeniumRun;
    }

    public String getXeniumComment() {
        return this.xeniumComment;
    }

    public void setXeniumComment(String xeniumComment) {
        this.xeniumComment = xeniumComment;
    }

    public String getSolution() {
        return this.solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }

    public String getStainQcComment() {
        return this.stainQcComment;
    }

    public void setStainQcComment(String stainQcComment) {
        this.stainQcComment = stainQcComment;
    }

    public String getAmplificationCycles() {
        return this.amplificationCycles;
    }

    public void setAmplificationCycles(String amplificationCycles) {
        this.amplificationCycles = amplificationCycles;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public String getFlagDescription() {
        return this.flagDescription;
    }

    public void setFlagDescription(String flagDescription) {
        this.flagDescription = flagDescription;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseEntry that = (ReleaseEntry) o;
        return (Objects.equals(this.labware, that.labware)
                && Objects.equals(this.slot, that.slot)
                && Objects.equals(this.sample, that.sample)
                && Objects.equals(this.lastSection, that.lastSection)
                && Objects.equals(this.sourceBarcode, that.sourceBarcode)
                && Objects.equals(this.sectionThickness, that.sectionThickness)
                && Objects.equals(this.sourceAddress, that.sourceAddress)
                && Objects.equals(this.storageAddress, that.storageAddress)
                && Objects.equals(this.stainType, that.stainType)
                && Objects.equals(this.bondBarcode, that.bondBarcode)
                && Objects.equals(this.coverage, that.coverage)
                && Objects.equals(this.reagentPlateType, that.reagentPlateType)
                && Objects.equals(this.reagentSource, that.reagentSource)
                && Objects.equals(this.cq, that.cq)
                && Objects.equals(this.visiumConcentration, that.visiumConcentration)
                && Objects.equals(this.visiumConcentrationType, that.visiumConcentrationType)
                && Objects.equals(this.rnascopePlex, that.rnascopePlex)
                && Objects.equals(this.ihcPlex, that.ihcPlex)
                && Objects.equals(this.sectionDate, that.sectionDate)
                && Objects.equals(this.permTime, that.permTime)
                && Objects.equals(this.samplePosition, that.samplePosition)
                && Objects.equals(this.sectionComment, that.sectionComment)
                && Objects.equals(this.tagData, that.tagData)
                && Objects.equals(this.hybridStart, that.hybridStart)
                && Objects.equals(this.hybridEnd, that.hybridEnd)
                && Objects.equals(this.xeniumPlex, that.xeniumPlex)
                && Objects.equals(this.xeniumProbe, that.xeniumProbe)
                && Objects.equals(this.xeniumProbeLot, that.xeniumProbeLot)
                && Objects.equals(this.hybridComment, that.hybridComment)
                && Objects.equals(this.xeniumStart, that.xeniumStart)
                && Objects.equals(this.xeniumEnd, that.xeniumEnd)
                && Objects.equals(this.xeniumRoi, that.xeniumRoi)
                && Objects.equals(this.xeniumReagentALot, that.xeniumReagentALot)
                && Objects.equals(this.xeniumReagentBLot, that.xeniumReagentBLot)
                && Objects.equals(this.xeniumCassettePosition, that.xeniumCassettePosition)
                && Objects.equals(this.xeniumRun, that.xeniumRun)
                && Objects.equals(this.xeniumComment, that.xeniumComment)
                && Objects.equals(this.solution, that.solution)
                && Objects.equals(this.stainQcComment, that.stainQcComment)
                && Objects.equals(this.amplificationCycles, that.amplificationCycles)
                && Objects.equals(this.equipment, that.equipment)
                && Objects.equals(this.flagDescription, that.flagDescription)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, sample);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ReleaseEntry")
                .add("labware", labware==null ? null : labware.getBarcode())
                .add("slot", slot)
                .add("sample", sample==null ? null : sample.getId())
                .add("lastSection", lastSection)
                .add("sourceBarcode", sourceBarcode)
                .add("sectionThickness", sectionThickness)
                .add("sourceAddress", sourceAddress)
                .add("storageAddress", storageAddress)
                .add("stainType", stainType)
                .add("bondBarcode", bondBarcode)
                .add("coverage", coverage)
                .add("reagentPlateType", reagentPlateType)
                .add("reagentSource", reagentSource)
                .add("cq", cq)
                .add("visiumConcentration", visiumConcentration)
                .add("visiumConcentrationType", visiumConcentrationType)
                .add("rnascopePlex", rnascopePlex)
                .add("ihcPlex", ihcPlex)
                .add("sectionDate", sectionDate)
                .add("permTime", permTime)
                .add("samplePosition", samplePosition)
                .add("sectionComment", sectionComment)
                .addIfNotEmpty("tagData", tagData)
                .add("hybridStart", hybridStart)
                .add("hybridEnd", hybridEnd)
                .add("xeniumPlex", xeniumPlex)
                .add("xeniumProbe", xeniumProbe)
                .add("xeniumProbeLot", xeniumProbeLot)
                .add("hybridComment", hybridComment)
                .add("xeniumStart", xeniumStart)
                .add("xeniumEnd", xeniumEnd)
                .add("xeniumRoi", xeniumRoi)
                .add("xeniumReagentALot", xeniumReagentALot)
                .add("xeniumReagentBLot", xeniumReagentBLot)
                .add("xeniumCassettePosition", xeniumCassettePosition)
                .add("xeniumRun", xeniumRun)
                .add("xeniumComment", xeniumComment)
                .add("solution", solution)
                .add("stainQcComment", stainQcComment)
                .add("amplificationCycles", amplificationCycles)
                .add("equipment", equipment)
                .add("flagDescription", flagDescription)
                .reprStringValues()
                .omitNullValues()
                .toString();
    }
}
