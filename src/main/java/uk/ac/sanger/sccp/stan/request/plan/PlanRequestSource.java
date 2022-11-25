package uk.ac.sanger.sccp.stan.request.plan;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * @author dr6
 */
public class PlanRequestSource {
    private String barcode;
    private Address address;

    public PlanRequestSource() {}

    public PlanRequestSource(String barcode, Address address) {
        this.barcode = barcode;
        this.address = address;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanRequestSource that = (PlanRequestSource) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.address, that.address));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, address);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("barcode", barcode)
                .add("address", address)
                .reprStringValues()
                .toString();
    }
}
