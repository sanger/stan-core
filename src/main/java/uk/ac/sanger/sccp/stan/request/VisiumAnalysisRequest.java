package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * A request to record visium analysis and select one of the already-recorded perm times.
 * @author dr6
 */
public class VisiumAnalysisRequest {
    private String barcode;
    private String workNumber;
    private Address selectedAddress;
    private Integer selectedTime;

    public VisiumAnalysisRequest() {}

    public VisiumAnalysisRequest(String barcode, String workNumber, Address selectedAddress, Integer selectedTime) {
        this.barcode = barcode;
        this.workNumber = workNumber;
        this.selectedAddress = selectedAddress;
        this.selectedTime = selectedTime;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    public Address getSelectedAddress() {
        return this.selectedAddress;
    }

    public void setSelectedAddress(Address selectedAddress) {
        this.selectedAddress = selectedAddress;
    }

    public Integer getSelectedTime() {
        return this.selectedTime;
    }

    public void setSelectedTime(Integer selectedTime) {
        this.selectedTime = selectedTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VisiumAnalysisRequest that = (VisiumAnalysisRequest) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.selectedAddress, that.selectedAddress)
                && Objects.equals(this.selectedTime, that.selectedTime));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, workNumber, selectedAddress, selectedTime);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .addRepr("barcode", barcode)
                .addRepr("workNumber", workNumber)
                .add("selectedAddress", selectedAddress)
                .add("selectedTime", selectedTime)
                .toString();
    }
}
