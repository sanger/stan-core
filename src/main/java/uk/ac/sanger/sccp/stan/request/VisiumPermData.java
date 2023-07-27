package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * The info about visium time measurements
 * @author dr6
 */
public class VisiumPermData {
    public static class AddressPermData {
        private Address address;
        private Integer seconds;
        private ControlType controlType;
        private boolean selected;

        public AddressPermData() {}

        public AddressPermData(Address address, Integer seconds, ControlType controlType, boolean selected) {
            this.address = address;
            this.seconds = seconds;
            this.controlType = controlType;
            this.selected = selected;
        }

        public AddressPermData(Address address, ControlType controlType) {
            this(address, null, controlType, false);
        }

        public AddressPermData(Address address, Integer seconds) {
            this(address, seconds, null, false);
        }

        public Address getAddress() {
            return this.address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        public Integer getSeconds() {
            return this.seconds;
        }

        public void setSeconds(Integer seconds) {
            this.seconds = seconds;
        }

        public boolean isSelected() {
            return this.selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AddressPermData that = (AddressPermData) o;
            return (this.selected == that.selected
                    && Objects.equals(this.address, that.address)
                    && this.controlType==that.controlType
                    && Objects.equals(this.seconds, that.seconds));
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, seconds, controlType, selected);
        }

        @Override
        public String toString() {
            return String.format("(%s: %s%s%s)", address, seconds==null ? "" : seconds, controlType==null ? "" : controlType,
                    selected ? ", selected":"");
        }
    }

    private Labware labware;
    private List<AddressPermData> addressPermData;
    private List<SamplePositionResult> samplePositionResults;

    public VisiumPermData(Labware labware, List<AddressPermData> addressPermData, List<SamplePositionResult> samplePositionResults) {
        this.labware = labware;
        this.samplePositionResults = samplePositionResults;
        setAddressPermData(addressPermData);
    }

    public Labware getLabware() {
        return this.labware;
    }

    public void setLabware(Labware labware) {
        this.labware = labware;
    }

    public List<AddressPermData> getAddressPermData() {
        return this.addressPermData;
    }

    public void setAddressPermData(List<AddressPermData> addressPermData) {
        this.addressPermData = (addressPermData==null ? List.of() : addressPermData);
    }

    public List<SamplePositionResult> getSamplePositionResults() {
        return samplePositionResults;
    }

    public void setSamplePositionResults(List<SamplePositionResult> samplePositionResults) {
        this.samplePositionResults = samplePositionResults;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VisiumPermData that = (VisiumPermData) o;
        return (Objects.equals(this.labware, that.labware)
                && Objects.equals(this.addressPermData, that.addressPermData));
    }

    @Override
    public int hashCode() {
        return Objects.hash(labware, addressPermData);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("VisiumPermData")
                .add("labware", labware)
                .add("addressPermData", addressPermData)
                .toString();
    }
}
