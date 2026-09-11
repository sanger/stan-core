package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * A barcode and a sample id
 * @author dr6
 */
public class BarcodeSampleId {
    private String barcode;
    private int sampleId;

    public BarcodeSampleId() {}

    public BarcodeSampleId(String barcode, int sampleId) {
        this.barcode = barcode;
        this.sampleId = sampleId;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public int getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(int sampleId) {
        this.sampleId = sampleId;
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .addRepr("barcode", barcode)
                .add("sampleId", sampleId)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BarcodeSampleId that = (BarcodeSampleId) o;
        return (this.sampleId == that.sampleId
                && Objects.equals(this.barcode, that.barcode));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, sampleId);
    }
}
