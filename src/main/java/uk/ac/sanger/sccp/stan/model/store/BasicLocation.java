package uk.ac.sanger.sccp.stan.model.store;

import uk.ac.sanger.sccp.stan.model.Address;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * The location barcode and item address (if any) of an item in storage.
 * @author dr6
 */
public class BasicLocation {
    private String barcode;
    private String name;
    private Address address;
    private Integer addressIndex;
    private int numStored;

    public BasicLocation() {}

    public BasicLocation(String barcode, Address address) {
        this(barcode, null, address, null, 0);
    }

    public BasicLocation(String barcode, String name, Address address, Integer addressIndex, int numStored) {
        this.barcode = barcode;
        this.name = name;
        this.address = address;
        this.addressIndex = addressIndex;
        this.numStored = numStored;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Integer getAddressIndex() {
        return this.addressIndex;
    }

    public void setAddressIndex(Integer addressIndex) {
        this.addressIndex = addressIndex;
    }

    /** The number of items directly stored in this location */
    public int getNumStored() {
        return this.numStored;
    }

    public void setNumStored(int numStored) {
        this.numStored = numStored;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BasicLocation that = (BasicLocation) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.address, that.address)
                && Objects.equals(this.addressIndex, that.addressIndex)
                && this.numStored==that.numStored);
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, address);
    }

    @Override
    public String toString() {
        return String.format("(%s:%s:%s:%s)", barcode, repr(name), address, addressIndex);
    }
}
