package uk.ac.sanger.sccp.stan.request.plan;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.Address;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * A request for an action (i.e. one sample moving from one slot to another slot) in a planned operation.
 * @author dr6
 */
public class PlanRequestAction {
    private List<Address> addresses = List.of();
    private int sampleId;
    private PlanRequestSource source;
    private String sampleThickness;

    public PlanRequestAction() {}

    public PlanRequestAction(Address address, int sampleId, PlanRequestSource source, String sampleThickness) {
        this.addresses = address==null ? List.of() : List.of(address);
        this.sampleId = sampleId;
        this.source = source;
        this.sampleThickness = sampleThickness;
    }

    public List<Address> getAddresses() {
        return this.addresses;
    }

    public void setAddresses(List<Address> addresses) {
        this.addresses = nullToEmpty(addresses);
    }

    public int getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(int sampleId) {
        this.sampleId = sampleId;
    }

    public PlanRequestSource getSource() {
        return this.source;
    }

    public void setSource(PlanRequestSource source) {
        this.source = source;
    }

    public String getSampleThickness() {
        return this.sampleThickness;
    }

    public void setSampleThickness(String sampleThickness) {
        this.sampleThickness = sampleThickness;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanRequestAction that = (PlanRequestAction) o;
        return (this.sampleId==that.sampleId
                && Objects.equals(this.addresses, that.addresses)
                && Objects.equals(this.source, that.source)
                && Objects.equals(this.sampleThickness, that.sampleThickness));
    }

    @Override
    public int hashCode() {
        return Objects.hash(addresses, sampleId, source);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("addresses", addresses)
                .add("sampleId", sampleId)
                .add("source", source)
                .add("sampleThickness", sampleThickness)
                .toString();
    }
}
