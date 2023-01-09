package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;

import java.util.Objects;

/**
 * An address (row,column) and a comment id.
 * @author dr6
 */
public class AddressCommentId {
    private Address address;
    private Integer commentId;

    public AddressCommentId() {
    }

    public AddressCommentId(Address address, Integer commentId) {
        this.address = address;
        this.commentId = commentId;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Integer getCommentId() {
        return this.commentId;
    }

    public void setCommentId(Integer commentId) {
        this.commentId = commentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddressCommentId that = (AddressCommentId) o;
        return (Objects.equals(this.address, that.address)
                && Objects.equals(this.commentId, that.commentId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, commentId);
    }

    @Override
    public String toString() {
        return String.format("(%s, %s)", address, commentId);
    }
}
