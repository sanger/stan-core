package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.SlideCosting;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * A request to record segmentation on one or more labware.
 * @author dr6
 */
public class SegmentationRequest {
    private String operationType;
    private List<SegmentationLabware> labware = List.of();

    // Deserialization constructor
    public SegmentationRequest() {}

    public SegmentationRequest(String operationType, List<SegmentationLabware> labware) {
        setOperationType(operationType);
        setLabware(labware);
    }

    /**
     * The name of the operation to record.
     */
    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * The details of the labware involved.
     */
    public List<SegmentationLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<SegmentationLabware> labware) {
        this.labware = nullToEmpty(labware);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .addRepr("operationType", operationType)
                .add("labware", labware)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SegmentationRequest that = (SegmentationRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, labware);
    }

    /**
     * Details about labware in a segmentation request.
     */
    public static class SegmentationLabware {
        private String barcode;
        private String workNumber;
        private List<Integer> commentIds = List.of();
        private SlideCosting costing;
        private LocalDateTime performed;

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
         * The work number to link to the operation.
         */
        public String getWorkNumber() {
            return this.workNumber;
        }

        public void setWorkNumber(String workNumber) {
            this.workNumber = workNumber;
        }

        /**
         * The comment ids to link to the operation.
         */
        public List<Integer> getCommentIds() {
            return this.commentIds;
        }

        public void setCommentIds(List<Integer> commentIds) {
            this.commentIds = nullToEmpty(commentIds);
        }

        /**
         * The costing of the operation.
         */
        public SlideCosting getCosting() {
            return this.costing;
        }

        public void setCosting(SlideCosting costing) {
            this.costing = costing;
        }

        /**
         * The time with which the operation should be recorded.
         */
        public LocalDateTime getPerformed() {
            return this.performed;
        }

        public void setPerformed(LocalDateTime performed) {
            this.performed = performed;
        }

        @Override
        public String toString() {
            return BasicUtils.describe(this)
                    .add("barcode", barcode)
                    .add("workNumber", workNumber)
                    .add("commentIds", commentIds)
                    .add("costing", costing)
                    .add("performed", performed==null ? null : performed.toString())
                    .reprStringValues()
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SegmentationLabware that = (SegmentationLabware) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.workNumber, that.workNumber)
                    && Objects.equals(this.commentIds, that.commentIds)
                    && this.costing == that.costing
                    && Objects.equals(this.performed, that.performed));
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, workNumber, commentIds, costing, performed);
        }
    }
}