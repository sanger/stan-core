package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.Slot;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * A sample position, as returned through the GraphQL API.
 * @author dr6
 */
public class SamplePositionResult {
    private Integer slotId;
    private Address address;
    private Integer sampleId;
    private String region;
    private Integer operationId;

    public SamplePositionResult() {}

    public SamplePositionResult(Integer slotId, Address address, Integer sampleId, String region) {
        this.slotId = slotId;
        this.address = address;
        this.sampleId = sampleId;
        this.region = region;
    }

    public SamplePositionResult(Slot slot, Integer sampleId, String region, Integer operationId) {
        setSlot(slot);
        this.sampleId = sampleId;
        this.region = region;
        this.operationId = operationId;
    }

    public Integer getSlotId() {
        return this.slotId;
    }

    public void setSlotId(Integer slotId) {
        this.slotId = slotId;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public String getRegion() {
        return this.region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setSlot(Slot slot) {
        if (slot==null) {
            setSlotId(null);
            setAddress(null);
        } else {
            setSlotId(slot.getId());
            setAddress(slot.getAddress());
        }
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("slotId", slotId)
                .add("address", address)
                .add("sampleId", sampleId)
                .addRepr("region", region)
                .add("operationId", operationId)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SamplePositionResult that = (SamplePositionResult) o;
        return (Objects.equals(this.slotId, that.slotId)
                && Objects.equals(this.address, that.address)
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.region, that.region)
                && Objects.equals(this.operationId, that.operationId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotId, address, sampleId, region, operationId);
    }
}
