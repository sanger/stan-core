package uk.ac.sanger.sccp.stan.request;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Request to record QC and completion time for one or more labware.
 * @author dr6
 */
public class QCLabwareRequest {

    // region nested
    /**
     * Labware to be QC'd with comments and a completion time.
     * @author dr6
     */
    public static class QCLabware {
        private String barcode;
        private String workNumber;
        private LocalDateTime completion;
        private List<Integer> comments = List.of();

        public QCLabware() {}

        public QCLabware(String barcode, String workNumber, LocalDateTime completion, List<Integer> comments) {
            setBarcode(barcode);
            setWorkNumber(workNumber);
            setCompletion(completion);
            setComments(comments);
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
         * The work number to link to the operation.
         */
        public String getWorkNumber() {
            return this.workNumber;
        }

        public void setWorkNumber(String workNumber) {
            this.workNumber = workNumber;
        }

        /**
         * The time at which the process was completed.
         */
        public LocalDateTime getCompletion() {
            return this.completion;
        }

        public void setCompletion(LocalDateTime completion) {
            this.completion = completion;
        }

        /**
         * Zero or more comments applied to this labware in this operation.
         */
        public List<Integer> getComments() {
            return this.comments;
        }

        public void setComments(List<Integer> comments) {
            this.comments = nullToEmpty(comments);
        }

        @Override
        public String toString() {
            return String.format("(%s, %s, %s, %s)", repr(barcode), repr(workNumber), completion, comments);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QCLabware that = (QCLabware) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.workNumber, that.workNumber)
                    && Objects.equals(this.completion, that.completion)
                    && Objects.equals(this.comments, that.comments));
        }

        @Override
        public int hashCode() {
            return (barcode==null ? 0 : barcode.hashCode());
        }
    }
    // endregion

    private String operationType;
    private List<QCLabware> labware = List.of();

    public QCLabwareRequest() {}

    public QCLabwareRequest(String operationType, List<QCLabware> labware) {
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
     * The specifications of labware to QC.
     */
    public List<QCLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<QCLabware> labware) {
        this.labware = nullToEmpty(labware);
    }

    @Override
    public String toString() {
        return String.format("QCLabwareRequest(%s, %s)", repr(operationType), labware);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QCLabwareRequest that = (QCLabwareRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, labware);
    }
}