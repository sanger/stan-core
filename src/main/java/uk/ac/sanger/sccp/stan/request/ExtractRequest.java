package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * A request to perform extract operation
 * @author dr6
 */
public class ExtractRequest {
    private List<String> barcodes;
    private String labwareType;
    private String workNumber;

    public ExtractRequest() {
        this(null, null, null);
    }

    public ExtractRequest(List<String> barcodes, String labwareType, String workNumber) {
        setBarcodes(barcodes);
        this.labwareType = labwareType;
        this.workNumber = workNumber;
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
        ExtractRequest that = (ExtractRequest) o;
        return (this.barcodes.equals(that.barcodes)
                && Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.workNumber, that.workNumber));
    }

    @Override
    public int hashCode() {
        return barcodes.hashCode();
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ExtractRequest")
                .add("barcodes", barcodes)
                .add("labwareType", labwareType)
                .add("workNumber", workNumber)
                .reprStringValues()
                .toString();
    }
}
