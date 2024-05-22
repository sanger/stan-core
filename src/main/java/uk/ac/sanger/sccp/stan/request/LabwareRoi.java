package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.Sample;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * The regions of interest recorded in a particular labware.
 * @author dr6
 */
public class LabwareRoi {
    private String barcode;
    private List<RoiResult> rois = List.of();

    // Deserialisation constructor
    public LabwareRoi() {}

    public LabwareRoi(String barcode, List<RoiResult> rois) {
        setBarcode(barcode);
        setRois(rois);
    }

    /** The barcode of the labware. */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /** The regions of interest recorded in the labware, if any. */
    public List<RoiResult> getRois() {
        return this.rois;
    }

    public void setRois(List<RoiResult> rois) {
        this.rois = nullToEmpty(rois);
    }

    @Override
    public String toString() {
        return describe(this)
                .addRepr("barcode", barcode)
                .add("rois", rois)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != this.getClass()) return false;
        LabwareRoi that = (LabwareRoi) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.rois, that.rois)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, rois);
    }

    /**
     * A region of interest for a sample in an operation.
     * @author dr6
     */
    public static class RoiResult {
        private Integer slotId;
        private Address address;
        private Sample sample;
        private Integer operationId;
        private String roi;

        // Deserialisation constructor
        public RoiResult() {}

        public RoiResult(Integer slotId, Address address, Sample sample, Integer operationId, String roi) {
            this.slotId = slotId;
            this.address = address;
            this.sample = sample;
            this.operationId = operationId;
            this.roi = roi;
        }

        /** The sample in the ROI */
        public Sample getSample() {
            return this.sample;
        }

        public void setSample(Sample sample) {
            this.sample = sample;
        }

        /** The id of the slot. */
        public Integer getSlotId() {
            return this.slotId;
        }

        public void setSlotId(Integer slotId) {
            this.slotId = slotId;
        }

        /** The address of the slot. */
        public Address getAddress() {
            return this.address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        /** The id of the operation in which the ROI was recorded. */
        public Integer getOperationId() {
            return this.operationId;
        }

        public void setOperationId(Integer operationId) {
            this.operationId = operationId;
        }

        /** The description of the region of interest. */
        public String getRoi() {
            return this.roi;
        }

        public void setRoi(String roi) {
            this.roi = roi;
        }

        @Override
        public String toString() {
            return describe(this)
                    .add("sample", sample==null ? "null": "["+sample.getId()+"]")
                    .add("slotId", slotId)
                    .add("address", address)
                    .add("operationId", operationId)
                    .addRepr("roi", roi)
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != this.getClass()) return false;
            RoiResult that = (RoiResult) o;
            return (Objects.equals(this.sample, that.sample)
                    && Objects.equals(this.slotId, that.slotId)
                    && Objects.equals(this.address, that.address)
                    && Objects.equals(this.operationId, that.operationId)
                    && Objects.equals(this.roi, that.roi)
            );
        }

        @Override
        public int hashCode() {
            return Objects.hash(sample, slotId, address, operationId, roi);
        }
    }
}