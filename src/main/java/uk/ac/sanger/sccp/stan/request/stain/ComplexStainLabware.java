package uk.ac.sanger.sccp.stan.request.stain;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * Labware in a complex stain request
 * @author dr6
 */
public class ComplexStainLabware {
    private String barcode;
    private String bondBarcode;
    private int bondRun;
    private String workNumber;
    private Integer plexRNAscope;
    private Integer plexIHC;
    private StainPanel panel;

    public ComplexStainLabware() {}

    public ComplexStainLabware(String barcode, String bondBarcode, int bondRun, String workNumber,
                               Integer plexRNAscope, Integer plexIHC, StainPanel panel) {
        this.barcode = barcode;
        this.bondBarcode = bondBarcode;
        this.bondRun = bondRun;
        this.workNumber = workNumber;
        this.plexRNAscope = plexRNAscope;
        this.plexIHC = plexIHC;
        this.panel = panel;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getBondBarcode() {
        return this.bondBarcode;
    }

    public void setBondBarcode(String bondBarcode) {
        this.bondBarcode = bondBarcode;
    }

    public int getBondRun() {
        return this.bondRun;
    }

    public void setBondRun(int bondRun) {
        this.bondRun = bondRun;
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    public Integer getPlexRNAscope() {
        return this.plexRNAscope;
    }

    public void setPlexRNAscope(Integer plexRNAscope) {
        this.plexRNAscope = plexRNAscope;
    }

    public Integer getPlexIHC() {
        return this.plexIHC;
    }

    public void setPlexIHC(Integer plexIHC) {
        this.plexIHC = plexIHC;
    }

    public StainPanel getPanel() {
        return this.panel;
    }

    public void setPanel(StainPanel panel) {
        this.panel = panel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComplexStainLabware that = (ComplexStainLabware) o;
        return (this.bondRun == that.bondRun
                && this.panel == that.panel
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.bondBarcode, that.bondBarcode)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.plexRNAscope, that.plexRNAscope)
                && Objects.equals(this.plexIHC, that.plexIHC));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, bondBarcode, bondRun, workNumber, plexRNAscope, plexIHC, panel);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ComplexStainLabware")
                .addRepr("barcode", barcode)
                .addRepr("bondBarcode", bondBarcode)
                .add("bondRun", bondRun)
                .addRepr("workNumber", workNumber)
                .add("plexRNAscope", plexRNAscope)
                .add("plexIHC", plexIHC)
                .add("panel", panel)
                .toString();
    }
}
