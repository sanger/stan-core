package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * A request to record the completion of a process such as probe hybridisation qc
 * @author dr6
 */
public class CompletionRequest {
    // region nested classes
    /**
     * A comment against a particular sample at an address (in some particular labware).
     * @author dr6
     */
    public static class SampleAddressComment {
        private Integer sampleId;
        private Address address;
        private Integer commentId;

        public SampleAddressComment() {}

        public SampleAddressComment(Integer sampleId, Address address, Integer commentId) {
            this.sampleId = sampleId;
            this.address = address;
            this.commentId = commentId;
        }

        /**
         * The id of the sample for the comment.
         */
        public Integer getSampleId() {
            return this.sampleId;
        }

        public void setSampleId(Integer sampleId) {
            this.sampleId = sampleId;
        }

        /**
         * The address of the slot for the comment.
         */
        public Address getAddress() {
            return this.address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        /**
         * The id of the comment to record.
         */
        public Integer getCommentId() {
            return this.commentId;
        }

        public void setCommentId(Integer commentId) {
            this.commentId = commentId;
        }

        @Override
        public String toString() {
            return String.format("{%s in %s: %s}", sampleId, address, commentId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SampleAddressComment that = (SampleAddressComment) o;
            return (Objects.equals(this.sampleId, that.sampleId)
                    && Objects.equals(this.address, that.address)
                    && Objects.equals(this.commentId, that.commentId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(sampleId, address, commentId);
        }
    }

    /**
     * The comments against samples in a particular piece of labware.
     * @author dr6
     */
    public static class LabwareSampleComments {
        private String barcode;
        private LocalDateTime completion;
        private List<SampleAddressComment> comments = List.of();

        public LabwareSampleComments() {}

        public LabwareSampleComments(String barcode, LocalDateTime completion, List<SampleAddressComment> comments) {
            setBarcode(barcode);
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
         * The comments to record against particular samples in this labware.
         */
        public List<SampleAddressComment> getComments() {
            return this.comments;
        }

        public void setComments(List<SampleAddressComment> comments) {
            this.comments = nullToEmpty(comments);
        }

        /** The (optional) completion time of the process on this labware. */
        public LocalDateTime getCompletion() {
            return this.completion;
        }

        public void setCompletion(LocalDateTime completion) {
            this.completion = completion;
        }

        @Override
        public String toString() {
            return String.format("{%s (%s): %s}", barcode, completion, comments);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LabwareSampleComments that = (LabwareSampleComments) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.completion, that.completion)
                    && Objects.equals(this.comments, that.comments));
        }

        @Override
        public int hashCode() {
            return barcode==null ? 0 : barcode.hashCode();
        }
    }
    // endregion

    private String operationType;
    private String workNumber;
    private List<LabwareSampleComments> labware = List.of();

    public CompletionRequest() {}

    public CompletionRequest(String workNumber, String operationType, List<LabwareSampleComments> labware) {
        setWorkNumber(workNumber);
        setOperationType(operationType);
        setLabware(labware);
    }

    /**
     * The name of the type operations being recorded.
     */
    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * The work number associated with these operations.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /**
     * The details of the labware involved in this operation.
     */
    public List<LabwareSampleComments> getLabware() {
        return this.labware;
    }

    public void setLabware(List<LabwareSampleComments> labware) {
        this.labware = nullToEmpty(labware);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("CompletionRequest")
                .add("operationType", operationType)
                .add("workNumber", workNumber)
                .add("labware", labware)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompletionRequest that = (CompletionRequest) o;
        return (Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(workNumber, operationType, labware);
    }
}