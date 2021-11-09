package uk.ac.sanger.sccp.stan.model.store;

import uk.ac.sanger.sccp.stan.model.Address;

import java.util.Objects;

/**
 * The location barcode and item address (if any) of an item in storage.
 * @author dr6
 */
public class BasicLocation {
    private String barcode;
    private Address address;

    public BasicLocation() {}

    public BasicLocation(String barcode, Address address) {
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
        BasicLocation that = (BasicLocation) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.address, that.address));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, address);
    }

    @Override
    public String toString() {
        return String.format("(%s:%s)", barcode, address);
    }
}