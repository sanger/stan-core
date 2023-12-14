package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * Record the orientation state of labware.
 * @author dr6
 */
public class OrientationRequest {
    private String barcode;
    private String workNumber;
    private boolean correct;

    public OrientationRequest(String barcode, String workNumber, boolean correct) {
        this.barcode = barcode;
        this.workNumber = workNumber;
        this.correct = correct;
    }

    // Required for framework
    public OrientationRequest() {}

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
     * The work number to link to the operation.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /**
     * Is the orientation correct?
     */
    public boolean isCorrect() {
        return this.correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrientationRequest that = (OrientationRequest) o;
        return (this.correct == that.correct
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.workNumber, that.workNumber));
    }

    @Override
    public int hashCode() {
        return barcode==null ? 0 : barcode.hashCode();
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("barcode", barcode)
                .add("workNumber", workNumber)
                .add("correct", correct)
                .reprStringValues()
                .toString();
    }
}