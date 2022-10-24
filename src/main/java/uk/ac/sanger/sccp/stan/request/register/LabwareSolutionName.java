package uk.ac.sanger.sccp.stan.request.register;

import java.util.Objects;

/**
 * An association between a labware barcode and a solution
 */
public class LabwareSolutionName {
    private String barcode;
    private String solutionName;

    public LabwareSolutionName() {}

    public LabwareSolutionName(String barcode, String solutionName) {
        this.barcode = barcode;
        this.solutionName = solutionName;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getSolutionName() {
        return this.solutionName;
    }

    public void setSolutionName(String solutionName) {
        this.solutionName = solutionName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabwareSolutionName that = (LabwareSolutionName) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.solutionName, that.solutionName));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, solutionName);
    }

    @Override
    public String toString() {
        return barcode + ": " + solutionName;
    }
}
