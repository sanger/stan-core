package uk.ac.sanger.sccp.stan.request;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.LifeStage;

import java.util.Objects;

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
    private int replicateNumber;
    private String externalIdentifier;
    private int highestSection;
    private String labwareType;
    private String medium;
    private String mouldSize;
    private String fixative;

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

    public int getReplicateNumber() {
        return this.replicateNumber;
    }

    public void setReplicateNumber(int replicateNumber) {
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

    public String getMouldSize() {
        return this.mouldSize;
    }

    public void setMouldSize(String mouldSize) {
        this.mouldSize = mouldSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockRegisterRequest that = (BlockRegisterRequest) o;
        return (this.spatialLocation == that.spatialLocation
                && this.replicateNumber == that.replicateNumber
                && this.highestSection == that.highestSection
                && Objects.equals(this.donorIdentifier, that.donorIdentifier)
                && this.lifeStage == that.lifeStage
                && Objects.equals(this.hmdmc, that.hmdmc)
                && Objects.equals(this.tissueType, that.tissueType)
                && Objects.equals(this.externalIdentifier, that.externalIdentifier)
                && Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.medium, that.medium)
                && Objects.equals(this.fixative, that.fixative)
                && Objects.equals(this.mouldSize, that.mouldSize));
    }

    @Override
    public int hashCode() {
        return (externalIdentifier!=null ? externalIdentifier.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
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
                .add("mouldSize", mouldSize)
                .toString();
    }
}
