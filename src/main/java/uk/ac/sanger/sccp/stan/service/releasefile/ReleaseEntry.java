package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.stan.model.*;

import java.util.Objects;

/**
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

    public ReleaseEntry(Labware labware, Slot slot, Sample sample) {
        this.labware = labware;
        this.slot = slot;
        this.sample = sample;
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
                && Objects.equals(this.sourceAddress, that.sourceAddress)
                && Objects.equals(this.sectionThickness, that.sectionThickness));
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, sample);
    }
}
