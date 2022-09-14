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
    private Integer commentId;

    public SlotMeasurementRequest() {}

    public SlotMeasurementRequest(Address address, String name, String value, Integer commentId) {
        this.address = address;
        this.name = name;
        this.value = value;
        this.commentId = commentId;
    }

    /** Copy constructor */
    public SlotMeasurementRequest(SlotMeasurementRequest other) {
        this(other.getAddress(), other.getName(), other.getValue(), other.getCommentId());
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

    public Integer getCommentId() {
        return this.commentId;
    }

    public void setCommentId(Integer commentId) {
        this.commentId = commentId;
    }

    /**
     * Returns a new SMR copied from this, with the name and value as given
     * @param name the new name
     * @param value the new value
     * @return a new SMR like this, with the given name and value
     */
    public SlotMeasurementRequest withNameAndValue(String name, String value) {
        SlotMeasurementRequest smr = new SlotMeasurementRequest(this);
        smr.setName(name);
        smr.setValue(value);
        return smr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotMeasurementRequest that = (SlotMeasurementRequest) o;
        return (Objects.equals(this.address, that.address)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.value, that.value)
                && Objects.equals(this.commentId, that.commentId)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, name, value);
    }

    @Override
    public String toString() {
        return String.format("SlotMeasurementRequest(%s, %s, %s, commentId=%s)", address, repr(name), repr(value),
                commentId);
    }
}
