package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * A request to remove samples from particular slots in labware.
 * @author dr6
 */
public class CleanOutRequest {
    private String barcode;
    private List<Address> addresses = List.of();
    private String workNumber;

    public CleanOutRequest() {} // deserialisation constructor

    public CleanOutRequest(String barcode, List<Address> addresses, String workNumber) {
        setBarcode(barcode);
        setAddresses(addresses);
        setWorkNumber(workNumber);
    }

    /**
     * The barcode of the labware.
     */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /**
     * The addresses of the slots to clean out.
     */
    public List<Address> getAddresses() {
        return this.addresses;
    }

    public void setAddresses(List<Address> addresses) {
        this.addresses = nullToEmpty(addresses);
    }

    /**
     * The work number to link to the operation.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("barcode", barcode)
                .add("addresses", addresses)
                .add("workNumber", workNumber)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CleanOutRequest that = (CleanOutRequest) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.addresses, that.addresses)
                && Objects.equals(this.workNumber, that.workNumber));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, addresses, workNumber);
    }
}