package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.CassettePosition;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A request to record an analyser operation.
 * @author dr6
 */
public class AnalyserRequest {
    // region nested classes
    /**
     * Specifies a region of interest for a sample in a slot address.
     * @author dr6
     */
    public static class SampleROI {
        private Address address;
        private Integer sampleId;
        private String roi;

        public SampleROI() {}

        public SampleROI(Address address, Integer sampleId, String roi) {
            this.address = address;
            this.sampleId = sampleId;
            this.roi = roi;
        }

        /**
         * The address of the slot.
         */
        public Address getAddress() {
            return this.address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        /**
         * The id of the sample.
         */
        public Integer getSampleId() {
            return this.sampleId;
        }

        public void setSampleId(Integer sampleId) {
            this.sampleId = sampleId;
        }

        /**
         * The region of interest of the sample.
         */
        public String getRoi() {
            return this.roi;
        }

        public void setRoi(String roi) {
            this.roi = roi;
        }

        @Override
        public String toString() {
            return String.format("(%s, %s, %s)", address, sampleId, repr(roi));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SampleROI that = (SampleROI) o;
            return (Objects.equals(this.address, that.address)
                    && Objects.equals(this.sampleId, that.sampleId)
                    && Objects.equals(this.roi, that.roi));
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, sampleId);
        }
    }

    /**
     * The information about a particular item of labware in an analyser request.
     * @author dr6
     */
    public static class AnalyserLabware {
        private String barcode;
        private String workNumber;
        private CassettePosition position;
        private List<SampleROI> samples = List.of();

        public AnalyserLabware() {}

        public AnalyserLabware(String barcode, String workNumber, CassettePosition position, List<SampleROI> samples) {
            this.barcode = barcode;
            this.workNumber = workNumber;
            this.position = position;
            setSamples(samples);
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
         * The work number for the labware.
         */
        public String getWorkNumber() {
            return this.workNumber;
        }

        public void setWorkNumber(String workNumber) {
            this.workNumber = workNumber;
        }

        /**
         * The cassette position for the labware.
         */
        public CassettePosition getPosition() {
            return this.position;
        }

        public void setPosition(CassettePosition position) {
            this.position = position;
        }

        /**
         * The sample regions of interest in this labware.
         */
        public List<SampleROI> getSamples() {
            return this.samples;
        }

        public void setSamples(List<SampleROI> samples) {
            this.samples = nullToEmpty(samples);
        }

        @Override
        public String toString() {
            return String.format("{%s: %s, %s, %s}", position, repr(barcode), repr(workNumber), samples);
        }
    }
    // endregion

    private String operationType;
    private String lotNumber;
    private String runName;
    private LocalDateTime performed;
    private List<AnalyserLabware> labware = List.of();

    public AnalyserRequest() {}

    public AnalyserRequest(String operationType, String lotNumber, String runName, LocalDateTime performed,
                           List<AnalyserLabware> labware) {
        this.operationType = operationType;
        this.lotNumber = lotNumber;
        this.runName = runName;
        this.performed = performed;
        setLabware(labware);
    }

    /**
     * The name of the operation type to record.
     */
    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * The lot number for the decoding reagents.
     */
    public String getLotNumber() {
        return this.lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    /**
     * The run name.
     */
    public String getRunName() {
        return this.runName;
    }

    public void setRunName(String runName) {
        this.runName = runName;
    }

    /**
     * The time at which this operation was performed.
     */
    public LocalDateTime getPerformed() {
        return this.performed;
    }

    public void setPerformed(LocalDateTime performed) {
        this.performed = performed;
    }

    /**
     * The labware involved in this request.
     */
    public List<AnalyserLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<AnalyserLabware> labware) {
        this.labware = nullToEmpty(labware);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("AnalyserRequest")
                .add("operationType", operationType)
                .add("lotNumber", lotNumber)
                .add("runName", runName)
                .add("performed", performed)
                .add("labware", labware)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnalyserRequest that = (AnalyserRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.lotNumber, that.lotNumber)
                && Objects.equals(this.runName, that.runName)
                && Objects.equals(this.performed, that.performed)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, lotNumber, runName, performed, labware);
    }
}