package uk.ac.sanger.sccp.stan.request;

import com.google.common.base.MoreObjects;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
public class ReleaseRequest {
    private List<String> barcodes;
    private String destination;
    private String recipient;

    public ReleaseRequest() {
        setBarcodes(null);
    }

    public ReleaseRequest(List<String> barcodes, String destination, String recipient) {
        setBarcodes(barcodes);
        setDestination(destination);
        setRecipient(recipient);
    }

    public List<String> getBarcodes() {
        return this.barcodes;
    }

    public void setBarcodes(Collection<String> barcodes) {
        this.barcodes = (barcodes==null ? new ArrayList<>() : new ArrayList<>(barcodes));
    }

    public String getDestination() {
        return this.destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getRecipient() {
        return this.recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseRequest that = (ReleaseRequest) o;
        return (Objects.equals(this.barcodes, that.barcodes)
                && Objects.equals(this.destination, that.destination)
                && Objects.equals(this.recipient, that.recipient));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcodes, destination, recipient);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("barcodes", barcodes)
                .add("destination", repr(destination))
                .add("recipient", repr(recipient))
                .toString();
    }
}
