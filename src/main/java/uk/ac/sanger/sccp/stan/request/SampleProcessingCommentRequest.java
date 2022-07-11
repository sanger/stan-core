package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;

/**
 * A request to record some in-place ops with comments.
 * @author dr6
 */
public class SampleProcessingCommentRequest {
    private List<BarcodeAndCommentId> labware;

    public SampleProcessingCommentRequest() {
        this(null);
    }

    public SampleProcessingCommentRequest(List<BarcodeAndCommentId> labware) {
        setLabware(labware);
    }

    public List<BarcodeAndCommentId> getLabware() {
        return this.labware;
    }

    public void setLabware(List<BarcodeAndCommentId> labware) {
        this.labware = (labware==null ? List.of() : labware);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SampleProcessingCommentRequest that = (SampleProcessingCommentRequest) o;
        return this.labware.equals(that.labware);
    }

    @Override
    public int hashCode() {
        return labware.hashCode();
    }

    @Override
    public String toString() {
        return BasicUtils.describe("SampleProcessingCommentRequest")
                .add("labware", labware)
                .toString();
    }
}
