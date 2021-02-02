package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.stan.model.*;

/**
 * @author dr6
 */
public class ReleaseEntry {
    private final Labware labware;
    private final Slot slot;
    private final Sample sample;
    private Integer lastSection;
    private String originalBarcode;
    private String sectionThickness;

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

    public void setOriginalBarcode(String originalBarcode) {
        this.originalBarcode = originalBarcode;
    }

    public String getOriginalBarcode() {
        return this.originalBarcode;
    }

    public String getSectionThickness() {
        return this.sectionThickness;
    }

    public void setSectionThickness(String sectionThickness) {
        this.sectionThickness = sectionThickness;
    }
}
