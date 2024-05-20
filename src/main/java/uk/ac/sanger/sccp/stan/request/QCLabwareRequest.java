package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;

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
        private List<QCSampleComment> sampleComments = List.of();

        public QCLabware() {}

        public QCLabware(String barcode, String workNumber, LocalDateTime completion, List<Integer> comments,
                         List<QCSampleComment> sampleComments) {
            setBarcode(barcode);
            setWorkNumber(workNumber);
            setCompletion(completion);
            setComments(comments);
            setSampleComments(sampleComments);
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

        /* Zero or more comments on individual samples in particular slots. */
        public List<QCSampleComment> getSampleComments() {
            return this.sampleComments;
        }

        public void setSampleComments(List<QCSampleComment> sampleComments) {
            this.sampleComments = nullToEmpty(sampleComments);
        }

        @Override
        public String toString() {
            return String.format("(%s, %s, %s, %s, %s)", repr(barcode), repr(workNumber), completion, comments, sampleComments);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QCLabware that = (QCLabware) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.workNumber, that.workNumber)
                    && Objects.equals(this.completion, that.completion)
                    && Objects.equals(this.comments, that.comments)
                    && Objects.equals(this.sampleComments, that.sampleComments)
            );
        }

        @Override
        public int hashCode() {
            return (barcode==null ? 0 : barcode.hashCode());
        }
    }

    /**
     * A comment on a particular sample in a particular slot.
     */
    public static class QCSampleComment {
        private Address address;
        private Integer sampleId;
        private Integer commentId;

        // deserialisation constructor
        public QCSampleComment() {}

        public QCSampleComment(Address address, Integer sampleId, Integer commentId) {
            this.address = address;
            this.sampleId = sampleId;
            this.commentId = commentId;
        }

        /** The address of the slot. */
        public Address getAddress() {
            return this.address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        /** The ID of the sample. */
        public Integer getSampleId() {
            return this.sampleId;
        }

        public void setSampleId(Integer sampleId) {
            this.sampleId = sampleId;
        }

        /** The ID of the comment. */
        public Integer getCommentId() {
            return this.commentId;
        }

        public void setCommentId(Integer commentId) {
            this.commentId = commentId;
        }

        @Override
        public String toString() {
            return String.format("(%s %s: %s)", address, sampleId, commentId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != this.getClass()) return false;
            QCSampleComment that = (QCSampleComment) o;
            return (Objects.equals(this.address, that.address)
                    && Objects.equals(this.sampleId, that.sampleId)
                    && Objects.equals(this.commentId, that.commentId)
            );
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, sampleId, commentId);
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