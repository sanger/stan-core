package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * A request to process original tissue into blocks.
 * @author dr6
 */
public class TissueBlockRequest {
    private List<TissueBlockLabware> labware = List.of();
    private String workNumber;
    private List<String> discardSourceBarcodes = List.of();

    public TissueBlockRequest() {}

    public TissueBlockRequest(List<TissueBlockLabware> labware) {
        this(labware, null, null);
    }

    public TissueBlockRequest(List<TissueBlockLabware> labware, String workNumber, List<String> discardSourceBarcodes) {
        setLabware(labware);
        this.workNumber = workNumber;
        setDiscardSourceBarcodes(discardSourceBarcodes);
    }

    /**
     * The work number associated with this request.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /**
     * The labware (blocks) being created by this request.
     */
    public List<TissueBlockLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<TissueBlockLabware> labware) {
        this.labware = (labware==null ? List.of() : labware);
    }

    /**
     * Which source barcodes (if any) to discard as part of this request.
     */
    public List<String> getDiscardSourceBarcodes() {
        return this.discardSourceBarcodes;
    }

    public void setDiscardSourceBarcodes(List<String> discardSourceBarcodes) {
        this.discardSourceBarcodes = (discardSourceBarcodes==null ? List.of() : discardSourceBarcodes);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("TissueBlockRequest")
                .add("labware", labware)
                .addReprIfNotNull("workNumber", workNumber)
                .addIfNotEmpty("discardSourceBarcodes", discardSourceBarcodes)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TissueBlockRequest that = (TissueBlockRequest) o;
        return (Objects.equals(this.labware, that.labware)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.discardSourceBarcodes, that.discardSourceBarcodes));
    }

    @Override
    public int hashCode() {
        return labware.hashCode();
    }

    /**
     * The input about a new block being created.
     * @author dr6
     */
    public static class TissueBlockLabware {
        private String sourceBarcode;
        private String labwareType;
        private String preBarcode;
        private Integer commentId;
        private String replicate;
        private String medium;

        public TissueBlockLabware() {}

        public TissueBlockLabware(String sourceBarcode, String labwareType, String replicate, String medium) {
            this(sourceBarcode, labwareType, replicate, medium, null, null);
        }

        public TissueBlockLabware(String sourceBarcode, String labwareType, String replicate, String medium,
                                  String preBarcode, Integer commentId) {
            this.sourceBarcode = sourceBarcode;
            this.labwareType = labwareType;
            this.replicate = replicate;
            this.medium = medium;
            this.preBarcode = preBarcode;
            this.commentId = commentId;
        }

        /**
         * The original tissue barcode.
         */
        public String getSourceBarcode() {
            return this.sourceBarcode;
        }

        public void setSourceBarcode(String sourceBarcode) {
            this.sourceBarcode = sourceBarcode;
        }

        /**
         * The labware type for the new labware.
         */
        public String getLabwareType() {
            return this.labwareType;
        }

        public void setLabwareType(String labwareType) {
            this.labwareType = labwareType;
        }

        /**
         * The barcode of the new labware, if it is prebarcoded.
         */
        public String getPreBarcode() {
            return this.preBarcode;
        }

        public void setPreBarcode(String preBarcode) {
            this.preBarcode = preBarcode;
        }

        /**
         * The comment (if any) associated with this operation.
         */
        public Integer getCommentId() {
            return this.commentId;
        }

        public void setCommentId(Integer commentId) {
            this.commentId = commentId;
        }

        /**
         * The replicate number for the new block.
         */
        public String getReplicate() {
            return this.replicate;
        }

        public void setReplicate(String replicate) {
            this.replicate = replicate;
        }

        /**
         * The medium for the new block.
         */
        public String getMedium() {
            return this.medium;
        }

        public void setMedium(String medium) {
            this.medium = medium;
        }

        @Override
        public String toString() {
            return BasicUtils.describe("TissueBlockLabware")
                    .add("sourceBarcode", sourceBarcode)
                    .add("labwareType", labwareType)
                    .add("preBarcode", preBarcode)
                    .add("commentId", commentId)
                    .add("replicate", replicate)
                    .add("medium", medium)
                    .reprStringValues()
                    .omitNullValues()
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TissueBlockLabware that = (TissueBlockLabware) o;
            return (Objects.equals(this.sourceBarcode, that.sourceBarcode)
                    && Objects.equals(this.labwareType, that.labwareType)
                    && Objects.equals(this.preBarcode, that.preBarcode)
                    && Objects.equals(this.commentId, that.commentId)
                    && Objects.equals(this.replicate, that.replicate)
                    && Objects.equals(this.medium, that.medium));
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceBarcode, replicate);
        }
    }
}