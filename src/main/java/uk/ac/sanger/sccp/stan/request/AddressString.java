package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * An address and a string associated with that address.
 * @author dr6
 */
public class AddressString {
    private Address address;
    private String string;

    public AddressString(Address address, String string) {
        this.address = address;
        this.string = string;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public String getString() {
        return this.string;
    }

    public void setString(String string) {
        this.string = string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddressString that = (AddressString) o;
        return (Objects.equals(this.address, that.address)
                && Objects.equals(this.string, that.string));
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, string);
    }

    @Override
    public String toString() {
        return this.address+": "+repr(this.string);
    }
}