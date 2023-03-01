package uk.ac.sanger.sccp.stan.request;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A link between a labware barcode and a work number.
 * @author dr6
 */
public class SuggestedWork {
    private String barcode;
    private String workNumber;

    public SuggestedWork(String barcode, String workNumber) {
        this.barcode = barcode;
        this.workNumber = workNumber;
    }

    public SuggestedWork() {}

    /**
     * The barcode of the labware.
     */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /**
     * The work number of the suggested work.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SuggestedWork that = (SuggestedWork) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.workNumber, that.workNumber));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, workNumber);
    }

    @Override
    public String toString() {
        return repr(barcode)+": "+repr(workNumber);
    }
}