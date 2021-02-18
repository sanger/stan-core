package uk.ac.sanger.sccp.stan.request;

import com.google.common.base.MoreObjects;

import java.util.List;
import java.util.Objects;

/**
 * A request to record the destruction of some labware
 * @author dr6
 */
public class DestroyRequest {
    private List<String> barcodes;
    private Integer reasonId;

    public DestroyRequest() {}

    public DestroyRequest(List<String> barcodes, Integer reasonId) {
        this.barcodes = barcodes;
        this.reasonId = reasonId;
    }

    public List<String> getBarcodes() {
        return this.barcodes;
    }

    public void setBarcodes(List<String> barcodes) {
        this.barcodes = barcodes;
    }

    public Integer getReasonId() {
        return this.reasonId;
    }

    public void setReasonId(Integer reasonId) {
        this.reasonId = reasonId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DestroyRequest that = (DestroyRequest) o;
        return (Objects.equals(this.barcodes, that.barcodes)
                && Objects.equals(this.reasonId, that.reasonId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcodes, reasonId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("barcodes", barcodes)
                .add("reasonId", reasonId)
                .toString();
    }
}
