package uk.ac.sanger.sccp.stan.request;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * A request to save metrics.
 * @author dr6
 */
public class SampleMetricsRequest {
    private String operationType;
    private String barcode;
    private String workNumber;
    private String runName;
    private List<SampleMetric> metrics = List.of();

    // Deserialisation constructor
    public SampleMetricsRequest() {}

    public SampleMetricsRequest(String operationType, String barcode, String workNumber, String runName, List<SampleMetric> metrics) {
        setOperationType(operationType);
        setBarcode(barcode);
        setWorkNumber(workNumber);
        setRunName(runName);
        setMetrics(metrics);
    }

    /** The name of the operation to record. */
    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /** The labware barcode. */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /** The work number to link to the operation. */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /** The run name to link to the operation. */
    public String getRunName() {
        return this.runName;
    }

    public void setRunName(String runName) {
        this.runName = runName;
    }

    /** The metrics to save. */
    public List<SampleMetric> getMetrics() {
        return this.metrics;
    }

    public void setMetrics(List<SampleMetric> metrics) {
        this.metrics = nullToEmpty(metrics);
    }

    @Override
    public String toString() {
        return describe(this)
                .add("operationType", operationType)
                .add("barcode", barcode)
                .add("workNumber", workNumber)
                .add("runName", runName)
                .add("metrics", metrics)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != this.getClass()) return false;
        SampleMetricsRequest that = (SampleMetricsRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.runName, that.runName)
                && Objects.equals(this.metrics, that.metrics)
        );
    }

    @Override
    public int hashCode() {
        return barcode==null ? 0 : barcode.hashCode();
    }

    /**
     * A key and value about a region of interest
     * @author dr6
     */
    public static class SampleMetric {
        private String roi;
        private String name;
        private String value;

        // deserialisation constructor
        public SampleMetric() {}

        public SampleMetric(String roi, String name, String value) {
            this.roi = roi;
            this.name = name;
            this.value = value;
        }

        /** The region of interest for this metric. */
        public String getRoi() {
            return this.roi;
        }

        public void setRoi(String roi) {
            this.roi = roi;
        }

        /** The name of the metric. */
        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        /** The value of the metric. */
        public String getValue() {
            return this.value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return String.format("(%s %s:%s)", repr(roi), repr(name), repr(value));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != this.getClass()) return false;
            SampleMetric that = (SampleMetric) o;
            return (Objects.equals(this.roi, that.roi)
                    && Objects.equals(this.name, that.name)
                    && Objects.equals(this.value, that.value)
            );
        }

        @Override
        public int hashCode() {
            return Objects.hash(roi, name, value);
        }
    }
}