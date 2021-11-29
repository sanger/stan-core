package uk.ac.sanger.sccp.stan.request.stain;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

/**
 * Request to record a stain of a complex nature
 * @author dr6
 */
public class ComplexStainRequest {
    private String stainType;
    private int plex;
    private StainPanel panel;
    private List<ComplexStainLabware> labware;

    public ComplexStainRequest() {
        this(null, 0, null, null);
    }

    public ComplexStainRequest(String stainType, int plex, StainPanel panel, List<ComplexStainLabware> labware) {
        this.stainType = stainType;
        this.plex = plex;
        this.panel = panel;
        setLabware(labware);
    }

    public String getStainType() {
        return this.stainType;
    }

    public void setStainType(String stainType) {
        this.stainType = stainType;
    }

    public int getPlex() {
        return this.plex;
    }

    public void setPlex(int plex) {
        this.plex = plex;
    }

    public StainPanel getPanel() {
        return this.panel;
    }

    public void setPanel(StainPanel panel) {
        this.panel = panel;
    }

    public List<ComplexStainLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<ComplexStainLabware> labware) {
        this.labware = (labware==null ? new ArrayList<>() : labware);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComplexStainRequest that = (ComplexStainRequest) o;
        return (this.plex == that.plex
                && Objects.equals(this.stainType, that.stainType)
                && this.panel==that.panel
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(stainType, plex, panel, labware);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ComplexStainRequest")
                .addRepr("stainType", stainType)
                .add("plex", plex)
                .add("panel", panel)
                .add("labware", labware)
                .toString();
    }
}
