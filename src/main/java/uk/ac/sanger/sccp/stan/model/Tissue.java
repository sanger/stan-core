package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class Tissue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String externalName;
    private Integer replicate;
    @ManyToOne
    private SpatialLocation spatialLocation;
    @ManyToOne
    private Donor donor;
    @ManyToOne
    private MouldSize mouldSize;
    @ManyToOne
    private Medium medium;
    @ManyToOne
    private Hmdmc hmdmc;

    public Tissue() {}

    public Tissue(Integer id, String externalName, Integer replicate, SpatialLocation spatialLocation, Donor donor,
                  MouldSize mouldSize, Medium medium, Hmdmc hmdmc) {
        this.id = id;
        this.externalName = externalName;
        this.replicate = replicate;
        this.spatialLocation = spatialLocation;
        this.donor = donor;
        this.mouldSize = mouldSize;
        this.medium = medium;
        this.hmdmc = hmdmc;
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

    public Integer getReplicate() {
        return this.replicate;
    }

    public void setReplicate(Integer replicate) {
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

    public MouldSize getMouldSize() {
        return this.mouldSize;
    }

    public void setMouldSize(MouldSize mouldSize) {
        this.mouldSize = mouldSize;
    }

    public Medium getMedium() {
        return this.medium;
    }

    public void setMedium(Medium medium) {
        this.medium = medium;
    }

    public Hmdmc getHmdmc() {
        return this.hmdmc;
    }

    public void setHmdmc(Hmdmc hmdmc) {
        this.hmdmc = hmdmc;
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
                && Objects.equals(this.donor, that.donor));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }
}
