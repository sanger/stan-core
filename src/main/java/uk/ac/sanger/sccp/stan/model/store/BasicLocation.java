package uk.ac.sanger.sccp.stan.model.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private int numChildren;

    public BasicLocation() {}

    public BasicLocation(String barcode, Address address) {
        this(barcode, null, address, null, 0, 0);
    }

    public BasicLocation(String barcode, String name, Address address, Integer addressIndex, int numStored, int numChildren) {
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

    /** The number of locations directly inside this location */
    public int getNumChildren() {
        return this.numChildren;
    }

    public void setNumChildren(int numChildren) {
        this.numChildren = numChildren;
    }

    @JsonIgnore
    public boolean isLeaf() {
        return getNumChildren() == 0;
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
                && this.numStored==that.numStored
                && this.numChildren==that.numChildren
        );
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
