package uk.ac.sanger.sccp.stan.request.register;

import uk.ac.sanger.sccp.stan.model.LifeStage;

import java.time.LocalDate;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * The information required to register a block.
 * @author dr6
 */
public class BlockRegisterRequest {
    private String donorIdentifier;
    private LifeStage lifeStage;
    private String hmdmc;
    private String tissueType;
    private int spatialLocation;
    private String replicateNumber;
    private String externalIdentifier;
    private int highestSection;
    private String labwareType;
    private String medium;
    private String fixative;
    private String species;
    private boolean existingTissue;
    private LocalDate sampleCollectionDate;
    private String bioRiskCode;

    public String getDonorIdentifier() {
        return this.donorIdentifier;
    }

    public void setDonorIdentifier(String donorIdentifier) {
        this.donorIdentifier = donorIdentifier;
    }

    public LifeStage getLifeStage() {
        return this.lifeStage;
    }

    public void setLifeStage(LifeStage lifeStage) {
        this.lifeStage = lifeStage;
    }

    public String getHmdmc() {
        return this.hmdmc;
    }

    public void setHmdmc(String hmdmc) {
        this.hmdmc = hmdmc;
    }

    public String getTissueType() {
        return this.tissueType;
    }

    public void setTissueType(String tissueType) {
        this.tissueType = tissueType;
    }

    public int getSpatialLocation() {
        return this.spatialLocation;
    }

    public void setSpatialLocation(int spatialLocation) {
        this.spatialLocation = spatialLocation;
    }

    public String getReplicateNumber() {
        return this.replicateNumber;
    }

    public void setReplicateNumber(String replicateNumber) {
        this.replicateNumber = replicateNumber;
    }

    public String getExternalIdentifier() {
        return this.externalIdentifier;
    }

    public void setExternalIdentifier(String externalIdentifier) {
        this.externalIdentifier = externalIdentifier;
    }

    public int getHighestSection() {
        return this.highestSection;
    }

    public void setHighestSection(int highestSection) {
        this.highestSection = highestSection;
    }

    public String getLabwareType() {
        return this.labwareType;
    }

    public void setLabwareType(String labwareType) {
        this.labwareType = labwareType;
    }

    public String getMedium() {
        return this.medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public String getFixative() {
        return this.fixative;
    }

    public void setFixative(String fixative) {
        this.fixative = fixative;
    }

    public String getSpecies() {
        return this.species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public boolean isExistingTissue() {
        return this.existingTissue;
    }

    public void setExistingTissue(boolean existingTissue) {
        this.existingTissue = existingTissue;
    }

    public LocalDate getSampleCollectionDate() {
        return this.sampleCollectionDate;
    }

    public void setSampleCollectionDate(LocalDate sampleCollectionDate) {
        this.sampleCollectionDate = sampleCollectionDate;
    }

    public String getBioRiskCode() {
        return this.bioRiskCode;
    }

    public void setBioRiskCode(String bioRiskCode) {
        this.bioRiskCode = bioRiskCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockRegisterRequest that = (BlockRegisterRequest) o;
        return (this.spatialLocation == that.spatialLocation
                && Objects.equals(this.replicateNumber, that.replicateNumber)
                && this.highestSection == that.highestSection
                && this.existingTissue == that.existingTissue
                && Objects.equals(this.donorIdentifier, that.donorIdentifier)
                && this.lifeStage == that.lifeStage
                && Objects.equals(this.hmdmc, that.hmdmc)
                && Objects.equals(this.tissueType, that.tissueType)
                && Objects.equals(this.externalIdentifier, that.externalIdentifier)
                && Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.medium, that.medium)
                && Objects.equals(this.fixative, that.fixative)
                && Objects.equals(this.species, that.species)
                && Objects.equals(this.sampleCollectionDate, that.sampleCollectionDate)
                && Objects.equals(this.bioRiskCode, that.bioRiskCode)
        );
    }

    @Override
    public int hashCode() {
        return (externalIdentifier!=null ? externalIdentifier.hashCode() : 0);
    }

    @Override
    public String toString() {
        return describe(this)
                .add("donorIdentifier", donorIdentifier)
                .add("lifeStage", lifeStage)
                .add("hmdmc", hmdmc)
                .add("tissueType", tissueType)
                .add("spatialLocation", spatialLocation)
                .add("replicateNumber", replicateNumber)
                .add("externalIdentifier", externalIdentifier)
                .add("highestSection", highestSection)
                .add("labwareType", labwareType)
                .add("medium", medium)
                .add("fixative", fixative)
                .add("species", species)
                .add("existingTissue", existingTissue)
                .add("sampleCollectionDate", sampleCollectionDate)
                .add("bioRiskCode", bioRiskCode)
                .reprStringValues()
                .toString();
    }
}
