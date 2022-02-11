package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * A request to record permeabilisation on a single slide.
 * @author dr6
 */
public class RecordPermRequest {
    /**
     * Specifies the perm time or the control type on a particular address in the labware.
     */
    public static class PermData {
        private Address address;
        private Integer seconds;
        private ControlType controlType;
        private String controlBarcode;

        public PermData() {}

        public PermData(Address address, Integer seconds, ControlType controlType, String controlBarcode) {
            this.address = address;
            this.seconds = seconds;
            this.controlType = controlType;
            this.controlBarcode = controlBarcode;
        }

        public PermData(Address address, Integer seconds) {
            this(address, seconds, null, null);
        }

        public PermData(Address address, ControlType controlType) {
            this(address, null, controlType, null);
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

        public ControlType getControlType() {
            return this.controlType;
        }

        public void setControlType(ControlType controlType) {
            this.controlType = controlType;
        }

        public String getControlBarcode() {
            return this.controlBarcode;
        }

        public void setControlBarcode(String controlBarcode) {
            this.controlBarcode = controlBarcode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PermData that = (PermData) o;
            return (Objects.equals(this.address, that.address)
                    && Objects.equals(this.seconds, that.seconds)
                    && Objects.equals(this.controlBarcode, that.controlBarcode)
                    && this.controlType == that.controlType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, seconds, controlType, controlBarcode);
        }

        @Override
        public String toString() {
            return BasicUtils.describe(this)
                    .add("address", address)
                    .addIfNotNull("seconds", seconds)
                    .addIfNotNull("controlType", controlType)
                    .addReprIfNotNull("controlBarcode", controlBarcode)
                    .toString();
        }
    }

    private String barcode;
    private String workNumber;
    private List<PermData> permData;

    public RecordPermRequest() {
        this(null, null, null);
    }

    public RecordPermRequest(String barcode, List<PermData> permData, String workNumber) {
        this.barcode = barcode;
        this.workNumber = workNumber;
        setPermData(permData);
    }

    public RecordPermRequest(String barcode) {
        this(barcode, null, null);
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public List<PermData> getPermData() {
        return this.permData;
    }

    public void setPermData(List<PermData> permData) {
        this.permData = (permData==null ? List.of() : permData);
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordPermRequest that = (RecordPermRequest) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.permData, that.permData)
                && Objects.equals(this.workNumber, that.workNumber));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, permData, workNumber);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .addRepr("barcode", barcode)
                .add("permData", permData)
                .addRepr("workNumber", workNumber)
                .toString();
    }
}
