package uk.ac.sanger.sccp.stan.request.register;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.LifeStage;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One section in one labware of a section registration request.
 * @author dr6
 */
public class SectionRegisterContent {
    private String donorIdentifier;
    private LifeStage lifeStage;
    private String species;
    private Address address;
    private String hmdmc;
    private String externalIdentifier;
    private String tissueType;
    private Integer spatialLocation;
    private String replicateNumber;
    private String fixative;
    private String medium;
    private Integer sectionNumber;
    private Integer sectionThickness;
    private String region;
    private LocalDate dateSectioned;
    private String bioRiskCode;

    public SectionRegisterContent() {}

    public SectionRegisterContent(String donorIdentifier, LifeStage lifeStage, String species) {
        this.donorIdentifier = donorIdentifier;
        this.lifeStage = lifeStage;
        this.species = species;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public String getSpecies() {
        return this.species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getHmdmc() {
        return this.hmdmc;
    }

    public void setHmdmc(String hmdmc) {
        this.hmdmc = hmdmc;
    }

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

    public String getExternalIdentifier() {
        return this.externalIdentifier;
    }

    public void setExternalIdentifier(String externalIdentifier) {
        this.externalIdentifier = externalIdentifier;
    }

    public String getTissueType() {
        return this.tissueType;
    }

    public void setTissueType(String tissueType) {
        this.tissueType = tissueType;
    }

    public Integer getSpatialLocation() {
        return this.spatialLocation;
    }

    public void setSpatialLocation(Integer spatialLocation) {
        this.spatialLocation = spatialLocation;
    }

    public String getReplicateNumber() {
        return this.replicateNumber;
    }

    public void setReplicateNumber(String replicateNumber) {
        this.replicateNumber = replicateNumber;
    }

    public String getFixative() {
        return this.fixative;
    }

    public void setFixative(String fixative) {
        this.fixative = fixative;
    }

    public String getMedium() {
        return this.medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public Integer getSectionNumber() {
        return this.sectionNumber;
    }

    public void setSectionNumber(Integer sectionNumber) {
        this.sectionNumber = sectionNumber;
    }

    public Integer getSectionThickness() {
        return this.sectionThickness;
    }

    public void setSectionThickness(Integer sectionThickness) {
        this.sectionThickness = sectionThickness;
    }

    /** The region of the section in the slot, if any */
    public String getRegion() {
        return this.region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    /** The date the sample was sectioned */
    public LocalDate getDateSectioned() {
        return this.dateSectioned;
    }

    public void setDateSectioned(LocalDate dateSectioned) {
        this.dateSectioned = dateSectioned;
    }

    /** The biological risk code for this sample. */
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
        SectionRegisterContent that = (SectionRegisterContent) o;
        return (Objects.equals(this.address, that.address)
                && Objects.equals(this.species, that.species)
                && Objects.equals(this.hmdmc, that.hmdmc)
                && Objects.equals(this.donorIdentifier, that.donorIdentifier)
                && this.lifeStage == that.lifeStage
                && Objects.equals(this.externalIdentifier, that.externalIdentifier)
                && Objects.equals(this.tissueType, that.tissueType)
                && Objects.equals(this.spatialLocation, that.spatialLocation)
                && Objects.equals(this.replicateNumber, that.replicateNumber)
                && Objects.equals(this.fixative, that.fixative)
                && Objects.equals(this.medium, that.medium)
                && Objects.equals(this.sectionNumber, that.sectionNumber)
                && Objects.equals(this.sectionThickness, that.sectionThickness)
                && Objects.equals(this.region, that.region)
                && Objects.equals(this.dateSectioned, that.dateSectioned)
                && Objects.equals(this.bioRiskCode, that.bioRiskCode)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, externalIdentifier);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("SectionRegisterContent")
                .add("address", address)
                .add("species", species)
                .add("hmdmc", hmdmc)
                .add("donorIdentifier", donorIdentifier)
                .add("lifeStage", lifeStage)
                .add("externalIdentifier", externalIdentifier)
                .add("tissueType", tissueType)
                .add("spatialLocation", spatialLocation)
                .add("replicateNumber", replicateNumber)
                .add("fixative", fixative)
                .add("medium", medium)
                .add("sectionNumber", sectionNumber)
                .add("sectionThickness", sectionThickness)
                .add("region", region)
                .add("dateSectioned", dateSectioned)
                .add("bioRiskCode", bioRiskCode)
                .reprStringValues()
                .omitNullValues()
                .toString();
    }
}
