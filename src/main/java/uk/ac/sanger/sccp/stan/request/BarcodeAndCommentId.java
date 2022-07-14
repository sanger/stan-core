package uk.ac.sanger.sccp.stan.request;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
public class BarcodeAndCommentId {
    private String barcode;
    private Integer commentId;

    public BarcodeAndCommentId() {}

    public BarcodeAndCommentId(String barcode, Integer commentId) {
        this.barcode = barcode;
        this.commentId = commentId;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

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
        BarcodeAndCommentId that = (BarcodeAndCommentId) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.commentId, that.commentId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, commentId);
    }

    public String toString() {
        return String.format("{barcode=%s, commentId=%s}", repr(barcode), commentId);
    }
}
