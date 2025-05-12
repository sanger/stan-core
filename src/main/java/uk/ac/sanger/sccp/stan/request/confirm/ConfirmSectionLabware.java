package uk.ac.sanger.sccp.stan.request.confirm;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * Part of a {@link ConfirmSectionRequest} applying to a single piece of labware
 * @author dr6
 */
public class ConfirmSectionLabware {
    private String barcode;
    private boolean cancelled;
    private List<ConfirmSection> confirmSections;
    private List<AddressCommentId> addressComments;
    private String workNumber;

    public ConfirmSectionLabware() {
        this(null, false, null, null, null);
    }

    public ConfirmSectionLabware(String barcode) {
        this(barcode, false, null, null, null);
    }

    public ConfirmSectionLabware(String barcode, boolean cancelled, Iterable<ConfirmSection> confirmSections,
                                 Iterable<AddressCommentId> addressComments, String workNumber) {
        setBarcode(barcode);
        setCancelled(cancelled);
        setConfirmSections(confirmSections);
        setAddressComments(addressComments);
        setWorkNumber(workNumber);
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

    public List<ConfirmSection> getConfirmSections() {
        return this.confirmSections;
    }

    public void setConfirmSections(Iterable<ConfirmSection> confirmSections) {
        this.confirmSections = newArrayList(confirmSections);
    }

    public List<AddressCommentId> getAddressComments() {
        return this.addressComments;
    }

    public void setAddressComments(Iterable<AddressCommentId> addressComments) {
        this.addressComments = newArrayList(addressComments);
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ConfirmSectionLabware")
                .add("barcode", barcode)
                .add("cancelled", cancelled)
                .add("confirmSections", confirmSections)
                .add("addressComments", addressComments)
                .add("workNumber", workNumber)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmSectionLabware that = (ConfirmSectionLabware) o;
        return (this.cancelled == that.cancelled
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.confirmSections, that.confirmSections)
                && Objects.equals(this.addressComments, that.addressComments)
                && Objects.equals(this.workNumber, that.workNumber)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, cancelled, confirmSections, addressComments);
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
