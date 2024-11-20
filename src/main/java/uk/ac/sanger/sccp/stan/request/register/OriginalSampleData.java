package uk.ac.sanger.sccp.stan.request.register;

import uk.ac.sanger.sccp.stan.model.LifeStage;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Data about registering a new original sample.
 * @author dr6
 */
public class OriginalSampleData {
    private String donorIdentifier;
    private LifeStage lifeStage;
    private String hmdmc;
    private String tissueType;
    private Integer spatialLocation;
    private String replicateNumber;
    private String externalIdentifier;
    private String labwareType;
    private String solution;
    private String fixative;
    private String species;
    private LocalDate sampleCollectionDate;
    private String workNumber;
    private String bioRiskCode;

    public OriginalSampleData() {}

    public OriginalSampleData(String donorIdentifier, LifeStage lifeStage, String hmdmc, String tissueType,
                              Integer spatialLocation, String replicateNumber, String externalIdentifier,
                              String labwareType, String solution, String fixative, String species,
                              LocalDate sampleCollectionDate, String workNumber, String bioRiskCode) {
        this.donorIdentifier = donorIdentifier;
        this.lifeStage = lifeStage;
        this.hmdmc = hmdmc;
        this.tissueType = tissueType;
        this.spatialLocation = spatialLocation;
        this.replicateNumber = replicateNumber;
        this.externalIdentifier = externalIdentifier;
        this.labwareType = labwareType;
        this.solution = solution;
        this.fixative = fixative;
        this.species = species;
        this.sampleCollectionDate = sampleCollectionDate;
        this.workNumber = workNumber;
        this.bioRiskCode = bioRiskCode;
    }

    /**
     * The string to use as the donor name.
     */
    public String getDonorIdentifier() {
        return this.donorIdentifier;
    }

    public void setDonorIdentifier(String donorIdentifier) {
        this.donorIdentifier = donorIdentifier;
    }

    /**
     * The life stage of the donor.
     */
    public LifeStage getLifeStage() {
        return this.lifeStage;
    }

    public void setLifeStage(LifeStage lifeStage) {
        this.lifeStage = lifeStage;
    }

    /**
     * The HMDMC to use for the tissue.
     */
    public String getHmdmc() {
        return this.hmdmc;
    }

    public void setHmdmc(String hmdmc) {
        this.hmdmc = hmdmc;
    }

    /**
     * The name of the tissue type (the organ from which the tissue is taken).
     */
    public String getTissueType() {
        return this.tissueType;
    }

    public void setTissueType(String tissueType) {
        this.tissueType = tissueType;
    }

    /**
     * The code for the spatial location from which the tissue is taken.
     */
    public Integer getSpatialLocation() {
        return this.spatialLocation;
    }

    public void setSpatialLocation(Integer spatialLocation) {
        this.spatialLocation = spatialLocation;
    }

    /**
     * The string to use for the replicate number of the tissue (optional).
     */
    public String getReplicateNumber() {
        return this.replicateNumber;
    }

    public void setReplicateNumber(String replicateNumber) {
        this.replicateNumber = replicateNumber;
    }

    /**
     * The external identifier used to identify the tissue.
     */
    public String getExternalIdentifier() {
        return this.externalIdentifier;
    }

    public void setExternalIdentifier(String externalIdentifier) {
        this.externalIdentifier = externalIdentifier;
    }

    /**
     * The name of the type of labware containing the sample.
     */
    public String getLabwareType() {
        return this.labwareType;
    }

    public void setLabwareType(String labwareType) {
        this.labwareType = labwareType;
    }

    /**
     * The solution used for the tissue.
     */
    public String getSolution() {
        return this.solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }

    /**
     * The fixative used for the tissue.
     */
    public String getFixative() {
        return this.fixative;
    }

    public void setFixative(String fixative) {
        this.fixative = fixative;
    }

    /**
     * The species of the donor.
     */
    public String getSpecies() {
        return this.species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    /**
     * The date the original sample was collected, if known.
     */
    public LocalDate getSampleCollectionDate() {
        return this.sampleCollectionDate;
    }

    public void setSampleCollectionDate(LocalDate sampleCollectionDate) {
        this.sampleCollectionDate = sampleCollectionDate;
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
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
        OriginalSampleData that = (OriginalSampleData) o;
        return (Objects.equals(this.donorIdentifier, that.donorIdentifier)
                && this.lifeStage == that.lifeStage
                && Objects.equals(this.hmdmc, that.hmdmc)
                && Objects.equals(this.tissueType, that.tissueType)
                && Objects.equals(this.spatialLocation, that.spatialLocation)
                && Objects.equals(this.replicateNumber, that.replicateNumber)
                && Objects.equals(this.externalIdentifier, that.externalIdentifier)
                && Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.solution, that.solution)
                && Objects.equals(this.fixative, that.fixative)
                && Objects.equals(this.species, that.species)
                && Objects.equals(this.sampleCollectionDate, that.sampleCollectionDate)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.bioRiskCode, that.bioRiskCode)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(donorIdentifier, lifeStage, hmdmc, tissueType, spatialLocation, replicateNumber,
                externalIdentifier, labwareType, solution, fixative, species, sampleCollectionDate, workNumber);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("donorIdentifier", donorIdentifier)
                .add("lifeStage", lifeStage)
                .add("hmdmc", hmdmc)
                .add("tissueType", tissueType)
                .add("spatialLocation", spatialLocation)
                .add("replicateNumber", replicateNumber)
                .add("externalIdentifier", externalIdentifier)
                .add("labwareType", labwareType)
                .add("solution", solution)
                .add("fixative", fixative)
                .add("species", species)
                .add("sampleCollectionDate", sampleCollectionDate)
                .add("workNumber", workNumber)
                .add("bioRiskCode", bioRiskCode)
                .reprStringValues()
                .toString();
    }
}