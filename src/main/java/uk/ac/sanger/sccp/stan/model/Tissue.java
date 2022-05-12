package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Class representing a piece of tissue from one donor, and from which numerous samples may be derived.
 * @author dr6
 */
@Entity
public class Tissue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String externalName;
    private String replicate;
    @ManyToOne
    private SpatialLocation spatialLocation;
    @ManyToOne
    private Donor donor;
    @ManyToOne
    private Medium medium;
    @ManyToOne
    private Fixative fixative;
    @ManyToOne
    private Hmdmc hmdmc;
    private LocalDate collectionDate;
    @ManyToOne
    private SolutionSample solutionSample;
    private Integer parentId;

    public Tissue() {}

    public Tissue(Integer id, String externalName, String replicate, SpatialLocation spatialLocation, Donor donor,
                  Medium medium, Fixative fixative, Hmdmc hmdmc, LocalDate collectionDate,
                  SolutionSample solutionSample, Integer parentId) {
        this.id = id;
        this.externalName = externalName;
        this.replicate = replicate;
        this.spatialLocation = spatialLocation;
        this.donor = donor;
        this.medium = medium;
        this.fixative = fixative;
        this.hmdmc = hmdmc;
        this.collectionDate = collectionDate;
        this.solutionSample = solutionSample;
        this.parentId = parentId;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getExternalName() {
        return this.externalName;
    }

    public void setExternalName(String externalName) {
        this.externalName = externalName;
    }

    public String getReplicate() {
        return this.replicate;
    }

    public void setReplicate(String replicate) {
        this.replicate = replicate;
    }

    public SpatialLocation getSpatialLocation() {
        return this.spatialLocation;
    }

    public void setSpatialLocation(SpatialLocation spatialLocation) {
        this.spatialLocation = spatialLocation;
    }

    public Donor getDonor() {
        return this.donor;
    }

    public void setDonor(Donor donor) {
        this.donor = donor;
    }

    public TissueType getTissueType() {
        return (this.spatialLocation==null ? null : spatialLocation.getTissueType());
    }

    public Medium getMedium() {
        return this.medium;
    }

    public void setMedium(Medium medium) {
        this.medium = medium;
    }

    public Fixative getFixative() {
        return this.fixative;
    }

    public void setFixative(Fixative fixative) {
        this.fixative = fixative;
    }

    public Hmdmc getHmdmc() {
        return this.hmdmc;
    }

    public void setHmdmc(Hmdmc hmdmc) {
        this.hmdmc = hmdmc;
    }

    public LocalDate getCollectionDate() {
        return this.collectionDate;
    }

    public void setCollectionDate(LocalDate collectionDate) {
        this.collectionDate = collectionDate;
    }

    public SolutionSample getSolutionSample() {
        return this.solutionSample;
    }

    public void setSolutionSample(SolutionSample solutionSample) {
        this.solutionSample = solutionSample;
    }

    /**
     * The original tissue from which this tissue was derived, if any
     * @return the id of the parent tissue
     */
    public Integer getParentId() {
        return this.parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tissue that = (Tissue) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.externalName, that.externalName)
                && Objects.equals(this.replicate, that.replicate)
                && Objects.equals(this.spatialLocation, that.spatialLocation)
                && Objects.equals(this.medium, that.medium)
                && Objects.equals(this.donor, that.donor)
                && Objects.equals(this.hmdmc, that.hmdmc)
                && Objects.equals(this.fixative, that.fixative)
                && Objects.equals(this.collectionDate, that.collectionDate)
                && Objects.equals(this.solutionSample, that.solutionSample)
                && Objects.equals(this.parentId, that.parentId)
        );
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("externalName", externalName)
                .add("replicate", replicate)
                .add("spatialLocation", spatialLocation)
                .add("donor", donor)
                .add("medium", medium)
                .add("fixative", fixative)
                .add("hmdmc", hmdmc)
                .add("collectionDate", collectionDate)
                .add("solutionSample", solutionSample)
                .add("parentId", parentId)
                .toString();
    }
}
