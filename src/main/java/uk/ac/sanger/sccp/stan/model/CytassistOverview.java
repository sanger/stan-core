package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * @author dr6
 */
@Entity
public class CytassistOverview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String workNumber;
    private Integer sampleId;
    private Integer section;
    private String sourceBarcode;
    private String sourceSlotAddress;
    private String sourceLabwareType;
    private String sourceExternalName;
    private LocalDateTime sourceLabwareCreated;
    private String stainType;
    private LocalDateTime stainPerformed;
    private LocalDateTime imagePerformed;
    private String probePanels;
    private LocalDateTime probeHybStart;
    private LocalDateTime probeHybEnd;
    private String cytassistBarcode;
    private String cytassistLabwareType;
    private String cytassistSlotAddress;
    private String cytassistLp;
    private LocalDateTime cytassistPerformed;
    private String tissueCoverage;
    private String qpcrResult;
    private String amplificationCq;
    private String dualIndexPlateType;
    private String dualIndexPlateWell;
    private String visiumConcentrationType;
    private String visiumConcentrationValue;
    private String visiumConcentrationAverageSize;
    private String visiumConcentrationRange;
    private String latestBarcode;
    private String latestLwState;
    private String latestBioState;
    private LocalDateTime latestBarcodeReleased;
    private String flags;
    private String users;

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public Integer getSection() {
        return this.section;
    }

    public void setSection(Integer section) {
        this.section = section;
    }

    public String getSourceBarcode() {
        return this.sourceBarcode;
    }

    public void setSourceBarcode(String sourceBarcode) {
        this.sourceBarcode = sourceBarcode;
    }

    public String getSourceSlotAddress() {
        return this.sourceSlotAddress;
    }

    public void setSourceSlotAddress(String sourceSlotAddress) {
        this.sourceSlotAddress = sourceSlotAddress;
    }

    public String getSourceLabwareType() {
        return this.sourceLabwareType;
    }

    public void setSourceLabwareType(String sourceLabwareType) {
        this.sourceLabwareType = sourceLabwareType;
    }

    public String getSourceExternalName() {
        return this.sourceExternalName;
    }

    public void setSourceExternalName(String sourceExternalName) {
        this.sourceExternalName = sourceExternalName;
    }

    public LocalDateTime getSourceLabwareCreated() {
        return this.sourceLabwareCreated;
    }

    public void setSourceLabwareCreated(LocalDateTime sourceLabwareCreated) {
        this.sourceLabwareCreated = sourceLabwareCreated;
    }

    public String getStainType() {
        return this.stainType;
    }

    public void setStainType(String stainType) {
        this.stainType = stainType;
    }

    public LocalDateTime getStainPerformed() {
        return this.stainPerformed;
    }

    public void setStainPerformed(LocalDateTime stainPerformed) {
        this.stainPerformed = stainPerformed;
    }

    public LocalDateTime getImagePerformed() {
        return this.imagePerformed;
    }

    public void setImagePerformed(LocalDateTime imagePerformed) {
        this.imagePerformed = imagePerformed;
    }

    public String getProbePanels() {
        return this.probePanels;
    }

    public void setProbePanels(String probePanels) {
        this.probePanels = probePanels;
    }

    public LocalDateTime getProbeHybStart() {
        return this.probeHybStart;
    }

    public void setProbeHybStart(LocalDateTime probeHybStart) {
        this.probeHybStart = probeHybStart;
    }

    public LocalDateTime getProbeHybEnd() {
        return this.probeHybEnd;
    }

    public void setProbeHybEnd(LocalDateTime probeHybEnd) {
        this.probeHybEnd = probeHybEnd;
    }

    public String getCytassistBarcode() {
        return this.cytassistBarcode;
    }

    public void setCytassistBarcode(String cytassistBarcode) {
        this.cytassistBarcode = cytassistBarcode;
    }

    public String getCytassistLabwareType() {
        return this.cytassistLabwareType;
    }

    public void setCytassistLabwareType(String cytassistLabwareType) {
        this.cytassistLabwareType = cytassistLabwareType;
    }

    public String getCytassistSlotAddress() {
        return this.cytassistSlotAddress;
    }

    public void setCytassistSlotAddress(String cytassistSlotAddress) {
        this.cytassistSlotAddress = cytassistSlotAddress;
    }

    public String getCytassistLp() {
        return this.cytassistLp;
    }

    public void setCytassistLp(String cytassistLp) {
        this.cytassistLp = cytassistLp;
    }

    public LocalDateTime getCytassistPerformed() {
        return this.cytassistPerformed;
    }

    public void setCytassistPerformed(LocalDateTime cytassistPerformed) {
        this.cytassistPerformed = cytassistPerformed;
    }

    public String getTissueCoverage() {
        return this.tissueCoverage;
    }

    public void setTissueCoverage(String tissueCoverage) {
        this.tissueCoverage = tissueCoverage;
    }

    public String getQpcrResult() {
        return this.qpcrResult;
    }

    public void setQpcrResult(String qpcrResult) {
        this.qpcrResult = qpcrResult;
    }

    public String getAmplificationCq() {
        return this.amplificationCq;
    }

    public void setAmplificationCq(String amplificationCq) {
        this.amplificationCq = amplificationCq;
    }

    public String getDualIndexPlateType() {
        return this.dualIndexPlateType;
    }

    public void setDualIndexPlateType(String dualIndexPlateType) {
        this.dualIndexPlateType = dualIndexPlateType;
    }

    public String getDualIndexPlateWell() {
        return this.dualIndexPlateWell;
    }

    public void setDualIndexPlateWell(String dualIndexPlateWell) {
        this.dualIndexPlateWell = dualIndexPlateWell;
    }

    public String getVisiumConcentrationType() {
        return this.visiumConcentrationType;
    }

    public void setVisiumConcentrationType(String visiumConcentrationType) {
        this.visiumConcentrationType = visiumConcentrationType;
    }

    public String getVisiumConcentrationValue() {
        return this.visiumConcentrationValue;
    }

    public void setVisiumConcentrationValue(String visiumConcentrationValue) {
        this.visiumConcentrationValue = visiumConcentrationValue;
    }

    public String getVisiumConcentrationAverageSize() {
        return this.visiumConcentrationAverageSize;
    }

    public void setVisiumConcentrationAverageSize(String visiumConcentrationAverageSize) {
        this.visiumConcentrationAverageSize = visiumConcentrationAverageSize;
    }

    public String getVisiumConcentrationRange() {
        return this.visiumConcentrationRange;
    }

    public void setVisiumConcentrationRange(String visiumConcentrationRange) {
        this.visiumConcentrationRange = visiumConcentrationRange;
    }

    public String getLatestBarcode() {
        return this.latestBarcode;
    }

    public void setLatestBarcode(String latestBarcode) {
        this.latestBarcode = latestBarcode;
    }

    public String getLatestLwState() {
        return this.latestLwState;
    }

    public void setLatestLwState(String latestLwState) {
        this.latestLwState = latestLwState;
    }

    public String getLatestBioState() {
        return this.latestBioState;
    }

    public void setLatestBioState(String latestBioState) {
        this.latestBioState = latestBioState;
    }

    public LocalDateTime getLatestBarcodeReleased() {
        return this.latestBarcodeReleased;
    }

    public void setLatestBarcodeReleased(LocalDateTime latestBarcodeReleased) {
        this.latestBarcodeReleased = latestBarcodeReleased;
    }

    public String getFlags() {
        return this.flags;
    }

    public void setFlags(String flags) {
        this.flags = flags;
    }

    public String getUsers() {
        return this.users;
    }

    public void setUsers(String users) {
        this.users = users;
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("id", id)
                .add("workNumber", workNumber)
                .add("sampleId", sampleId)
                .add("section", section)
                .add("sourceBarcode", sourceBarcode)
                .add("sourceSlotAddress", sourceSlotAddress)
                .add("sourceLabwareType", sourceLabwareType)
                .add("sourceExternalName", sourceExternalName)
                .add("sourceLabwareCreated", sourceLabwareCreated)
                .add("stainType", stainType)
                .add("stainPerformed", stainPerformed)
                .add("imagePerformed", imagePerformed)
                .add("probePanels", probePanels)
                .add("probeHybStart", probeHybStart)
                .add("probeHybEnd", probeHybEnd)
                .add("cytassistBarcode", cytassistBarcode)
                .add("cytassistLabwareType", cytassistLabwareType)
                .add("cytassistSlotAddress", cytassistSlotAddress)
                .add("cytassistLp", cytassistLp)
                .add("cytassistPerformed", cytassistPerformed)
                .add("tissueCoverage", tissueCoverage)
                .add("qpcrResult", qpcrResult)
                .add("amplificationCq", amplificationCq)
                .add("dualIndexPlateType", dualIndexPlateType)
                .add("dualIndexPlateWell", dualIndexPlateWell)
                .add("visiumConcentrationType", visiumConcentrationType)
                .add("visiumConcentrationValue", visiumConcentrationValue)
                .add("visiumConcentrationAverageSize", visiumConcentrationAverageSize)
                .add("visiumConcentrationRange", visiumConcentrationRange)
                .add("latestBarcode", latestBarcode)
                .add("latestLwState", latestLwState)
                .add("latestBioState", latestBioState)
                .add("latestBarcodeReleased", latestBarcodeReleased)
                .add("flags", flags)
                .add("users", users)
                .reprStringValues()
                .toString();
    }
}
