package uk.ac.sanger.sccp.stan.request.confirm;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.Address;

import java.util.*;

/**
 * The part of a {@link ConfirmOperationRequest confirmation request} applying to a single piece of labware
 * @author dr6
 */
public class ConfirmOperationLabware {
    private String barcode;
    private boolean cancelled;
    private Set<Address> cancelledAddresses;
    private List<AddressCommentId> addressComments;

    public ConfirmOperationLabware() {
        this(null, false, null, null);
    }

    public ConfirmOperationLabware(String barcode) {
        this(barcode, false, null, null);
    }

    public ConfirmOperationLabware(String barcode, boolean cancelled, Collection<Address> cancelledAddresses,
                                   Collection<AddressCommentId> addressComments) {
        setBarcode(barcode);
        setCancelled(cancelled);
        setCancelledAddresses(cancelledAddresses);
        setAddressComments(addressComments);
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public Set<Address> getCancelledAddresses() {
        return this.cancelledAddresses;
    }

    public void setCancelledAddresses(Collection<Address> cancelledAddresses) {
        this.cancelledAddresses = (cancelledAddresses==null ? new HashSet<>() : new HashSet<>(cancelledAddresses));
    }

    public List<AddressCommentId> getAddressComments() {
        return this.addressComments;
    }

    public void setAddressComments(Collection<AddressCommentId> addressComments) {
        this.addressComments = (addressComments==null ? new ArrayList<>() : new ArrayList<>(addressComments));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("barcode", barcode)
                .add("cancelled", cancelled)
                .add("cancelledAddresses", cancelledAddresses)
                .add("addressComments", addressComments)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmOperationLabware that = (ConfirmOperationLabware) o;
        return (this.cancelled == that.cancelled
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.cancelledAddresses, that.cancelledAddresses)
                && Objects.equals(this.addressComments, that.addressComments));
    }

    @Override
    public int hashCode() {
        return (barcode!=null ? barcode.hashCode() : 0);
    }

    public static class AddressCommentId {
        private Address address;
        private Integer commentId;

        public AddressCommentId() {}

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
}
