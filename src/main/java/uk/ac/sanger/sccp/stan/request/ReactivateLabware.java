package uk.ac.sanger.sccp.stan.request;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Request to undestroy/undiscard labware.
 * @author dr6
 */
public class ReactivateLabware {
    private String barcode;
    private String workNumber;
    private Integer commentId;

    public ReactivateLabware() {}

    public ReactivateLabware(String barcode, String workNumber, Integer commentId) {
        this.barcode = barcode;
        this.workNumber = workNumber;
        this.commentId = commentId;
    }

    /**
     * The barcode of the labware to reactivate.
     */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /**
     * The work number to associate with the reactivation.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /**
     * The comment id to associate with the reactivation.
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
        ReactivateLabware that = (ReactivateLabware) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.commentId, that.commentId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, workNumber, commentId);
    }

    @Override
    public String toString() {
        return String.format("{%s, commentId=%s, workNumber=%s}", repr(barcode), commentId, repr(workNumber));
    }
}