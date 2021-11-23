package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A slot address and a measurement spec
 * @author dr6
 */
public class SlotMeasurementRequest {
    private Address address;
    private String name;
    private String value;

    public SlotMeasurementRequest() {}

    public SlotMeasurementRequest(Address address, String name, String value) {
        this.address = address;
        this.name = name;
        this.value = value;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotMeasurementRequest that = (SlotMeasurementRequest) o;
        return (Objects.equals(this.address, that.address)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.value, that.value));
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, name, value);
    }

    @Override
    public String toString() {
        return String.format("SlotMeasurementRequest(%s, %s, %s)", address, repr(name), repr(value));
    }
}
