package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * A request to record FFPE processing.
 * @author dr6
 */
public class FFPEProcessingRequest {
    private String workNumber;
    private List<String> barcodes;
    private Integer commentId;

    public FFPEProcessingRequest() {
        this(null, null, null);
    }

    public FFPEProcessingRequest(String workNumber, List<String> barcodes, Integer commentId) {
        setWorkNumber(workNumber);
        setBarcodes(barcodes);
        setCommentId(commentId);
    }

    /**
     * The work number.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /**
     * The labware barcodes.
     */
    public List<String> getBarcodes() {
        return this.barcodes;
    }

    public void setBarcodes(List<String> barcodes) {
        this.barcodes = (barcodes==null ? List.of() : barcodes);
    }

    /**
     * The comment ID.
     */
    public Integer getCommentId() {
        return this.commentId;
    }

    public void setCommentId(Integer commentId) {
        this.commentId = commentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FFPEProcessingRequest that = (FFPEProcessingRequest) o;
        return (Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.barcodes, that.barcodes)
                && Objects.equals(this.commentId, that.commentId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(workNumber, barcodes, commentId);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("FFPEProcessingRequest")
                .addRepr("workNumber", workNumber)
                .add("barcodes", barcodes)
                .add("commentId", commentId)
                .toString();
    }
}