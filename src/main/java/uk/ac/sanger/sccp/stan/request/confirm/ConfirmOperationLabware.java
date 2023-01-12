package uk.ac.sanger.sccp.stan.request.confirm;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.request.AddressCommentId;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * The part of a {@link ConfirmOperationRequest confirmation request} applying to a single piece of labware
 * @author dr6
 */
public class ConfirmOperationLabware {
    private String barcode;
    private boolean cancelled;
    private Set<CancelPlanAction> cancelledActions;
    private List<AddressCommentId> addressComments;

    public ConfirmOperationLabware() {
        this(null, false, null, null);
    }

    public ConfirmOperationLabware(String barcode) {
        this(barcode, false, null, null);
    }

    public ConfirmOperationLabware(String barcode, boolean cancelled, Collection<CancelPlanAction> cancelledActions,
                                   Collection<AddressCommentId> addressComments) {
        setBarcode(barcode);
        setCancelled(cancelled);
        setCancelledActions(cancelledActions);
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

    public Set<CancelPlanAction> getCancelledActions() {
        return this.cancelledActions;
    }

    public void setCancelledActions(Collection<CancelPlanAction> cancelledActions) {
        this.cancelledActions = (cancelledActions==null ? new HashSet<>() : new HashSet<>(cancelledActions));
    }

    public List<AddressCommentId> getAddressComments() {
        return this.addressComments;
    }

    public void setAddressComments(Collection<AddressCommentId> addressComments) {
        this.addressComments = newArrayList(addressComments);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("barcode", barcode)
                .add("cancelled", cancelled)
                .add("cancelledActions", cancelledActions)
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
                && Objects.equals(this.cancelledActions, that.cancelledActions)
                && Objects.equals(this.addressComments, that.addressComments));
    }

    @Override
    public int hashCode() {
        return (barcode!=null ? barcode.hashCode() : 0);
    }

}
