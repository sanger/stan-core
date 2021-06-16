package uk.ac.sanger.sccp.stan.request.confirm;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * The information about a particular section in a confirmed operation
 * @author dr6
 */
public class ConfirmSection {
    private Address destinationAddress;
    private Integer sampleId;
    private Integer newSection;

    public ConfirmSection() {}

    public ConfirmSection(Address destinationAddress, Integer sampleId, Integer newSection) {
        this.destinationAddress = destinationAddress;
        this.sampleId = sampleId;
        this.newSection = newSection;
    }

    public Address getDestinationAddress() {
        return this.destinationAddress;
    }

    public void setDestinationAddress(Address destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public Integer getNewSection() {
        return this.newSection;
    }

    public void setNewSection(Integer newSection) {
        this.newSection = newSection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmSection that = (ConfirmSection) o;
        return (Objects.equals(this.destinationAddress, that.destinationAddress)
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.newSection, that.newSection));
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinationAddress, sampleId, newSection);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ConfirmSection")
                .add("destinationAddress", destinationAddress)
                .add("sampleId", sampleId)
                .add("newSection", newSection)
                .toString();
    }
}
