package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Information about an item to store. This is passed along with a location barcode.
 * @author dr6
 */
public class StoreInput {
    private String barcode;
    private Address address;

    public StoreInput() {}

    public StoreInput(String barcode, Address address) {
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
        StoreInput that = (StoreInput) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.address, that.address));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, address);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("StoreInput")
                .add("barcode", repr(barcode))
                .add("address", address)
                .toString();
    }
}
