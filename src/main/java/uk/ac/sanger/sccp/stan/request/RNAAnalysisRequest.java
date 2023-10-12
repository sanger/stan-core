package uk.ac.sanger.sccp.stan.request;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A request to record RNA analysis (of some kind) on multiple labware.
 * @author dr6
 */
public class RNAAnalysisRequest {
    /**
     * A request to record RNA analysis of some kind on a piece of labware
     */
    public static class RNAAnalysisLabware {
        private String barcode;
        private String workNumber;
        private Integer commentId;
        private List<StringMeasurement> measurements;

        public RNAAnalysisLabware(String barcode, String workNumber, Integer commentId, List<StringMeasurement> measurements) {
            this.barcode = barcode;
            this.workNumber = workNumber;
            this.commentId = commentId;
            setMeasurements(measurements);
        }

        /** The barcode of the labware to record the analysis on. */
        public String getBarcode() {
            return this.barcode;
        }

        /** Specifies the barcode of the labware to record the analysis on. */
        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        /** The work number to associate the analysis with, if any. */
        public String getWorkNumber() {
            return this.workNumber;
        }

        /** Sets the work number to associate the analysis with, if any. */
        public void setWorkNumber(String workNumber) {
            this.workNumber = workNumber;
        }

        /** The measurements to record */
        public List<StringMeasurement> getMeasurements() {
            return this.measurements;
        }

        /** Sets the measurements to record */
        public void setMeasurements(List<StringMeasurement> measurements) {
            this.measurements = (measurements==null ? new ArrayList<>() : measurements);
        }

        /** The id of a comment to record */
        public Integer getCommentId() {
            return this.commentId;
        }

        /** Sets the id of the comment to record */
        public void setCommentId(Integer commentId) {
            this.commentId = commentId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RNAAnalysisLabware that = (RNAAnalysisLabware) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.workNumber, that.workNumber)
                    && Objects.equals(this.commentId, that.commentId)
                    && Objects.equals(this.measurements, that.measurements));
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, workNumber, commentId, measurements);
        }

        @Override
        public String toString() {
            return String.format("{barcode=%s, workNumber=%s, commentId=%s, measurements=%s}",
                    repr(barcode), repr(workNumber), commentId, measurements);
        }
    }

    /** The type of analysis op being recorded. */
    private String operationType;

    /** The specification of the request on individual labware. */
    private List<RNAAnalysisLabware> labware;

    /** The equipment used for the analysis of the scanned labware(s) */
    private Integer equipmentId;


    /**
     * Creates a new RNA analysis request with the given labware specifications.
     * If you pass null for the labware, it will be replaced by an empty list.
     */
    public RNAAnalysisRequest(String operationType, List<RNAAnalysisLabware> labware, Integer equipmentId) {
        this.operationType = operationType;
        setLabware(labware);
        this.equipmentId = equipmentId;
    }

    /** The type of analysis op being recorded. */
    public String getOperationType() {
        return this.operationType;
    }

    /** Sets the type of analysis op being recorded. */
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /** The specification of the request on individual labware. */
    public List<RNAAnalysisLabware> getLabware() {
        return this.labware;
    }

    /**
     * Sets the specification of the request on individual labware.
     * If you pass null, it will be replaced by an empty list.
     */
    public void setLabware(List<RNAAnalysisLabware> labware) {
        this.labware = (labware==null ? new ArrayList<>() : labware);
    }

    public Integer getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Integer equipmentId) {
        this.equipmentId = equipmentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RNAAnalysisRequest that = (RNAAnalysisRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.labware, that.labware))
                && this.equipmentId == that.equipmentId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, labware, equipmentId);
    }

    @Override
    public String toString() {
        return String.format("RNAAnalysisRequest(%s, %s, %s)", repr(this.operationType), this.labware, this.equipmentId);
    }
}
