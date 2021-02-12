package uk.ac.sanger.sccp.stan.request;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A request to perform extract operation
 * @author dr6
 */
public class ExtractRequest {
    private List<String> barcodes;
    private String labwareType;

    public ExtractRequest() {
        this(null, null);
    }

    public ExtractRequest(List<String> barcodes, String labwareType) {
        setBarcodes(barcodes);
        this.labwareType = labwareType;
    }

    public List<String> getBarcodes() {
        return this.barcodes;
    }

    public void setBarcodes(Iterable<String> barcodes) {
        this.barcodes = newArrayList(barcodes);
    }

    public String getLabwareType() {
        return this.labwareType;
    }

    public void setLabwareType(String labwareType) {
        this.labwareType = labwareType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtractRequest that = (ExtractRequest) o;
        return this.barcodes.equals(that.barcodes) && Objects.equals(this.labwareType, that.labwareType);
    }

    @Override
    public int hashCode() {
        return barcodes.hashCode();
    }

    @Override
    public String toString() {
        return String.format("ExtractRequest(%s, %s)", repr(labwareType), barcodes);
    }
}
