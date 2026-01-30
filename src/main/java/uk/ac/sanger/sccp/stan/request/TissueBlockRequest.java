package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

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
     * The input about labware containing new blocks
     * @author dr6
     */
    public static class TissueBlockLabware {
        private String labwareType;
        private String preBarcode;
        private List<TissueBlockContent> contents = List.of();

        public TissueBlockLabware() {}

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

        public List<TissueBlockContent> getContents() {
            return this.contents;
        }

        public void setContents(List<TissueBlockContent> contents) {
            this.contents = nullToEmpty(contents);
        }

        @Override
        public String toString() {
            return BasicUtils.describe("TissueBlockLabware")
                    .add("labwareType", labwareType)
                    .add("preBarcode", preBarcode)
                    .add("contents", contents)
                    .reprStringValues()
                    .omitNullValues()
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TissueBlockLabware that = (TissueBlockLabware) o;
            return (Objects.equals(this.labwareType, that.labwareType)
                    && Objects.equals(this.preBarcode, that.preBarcode)
                    && Objects.equals(this.contents, that.contents));
        }

        @Override
        public int hashCode() {
            return Objects.hash(labwareType, preBarcode, contents);
        }
    }

    /**
     * The content of a block-sample
     */
    public static class TissueBlockContent {
        private String sourceBarcode;
        private Integer sourceSampleId;
        private List<Address> addresses = List.of();
        private Integer commentId;
        private String replicate;

        public TissueBlockContent() {}

        public String getSourceBarcode() {
            return this.sourceBarcode;
        }

        public void setSourceBarcode(String sourceBarcode) {
            this.sourceBarcode = sourceBarcode;
        }

        public Integer getSourceSampleId() {
            return this.sourceSampleId;
        }

        public void setSourceSampleId(Integer sourceSampleId) {
            this.sourceSampleId = sourceSampleId;
        }

        public List<Address> getAddresses() {
            return this.addresses;
        }

        public void setAddresses(List<Address> addresses) {
            this.addresses = nullToEmpty(addresses);
        }

        public Integer getCommentId() {
            return this.commentId;
        }

        public void setCommentId(Integer commentId) {
            this.commentId = commentId;
        }

        public String getReplicate() {
            return this.replicate;
        }

        public void setReplicate(String replicate) {
            this.replicate = replicate;
        }

        @Override
        public String toString() {
            return BasicUtils.describe(this)
                    .add("sourceBarcode", sourceBarcode)
                    .add("sourceSampleId", sourceSampleId)
                    .add("addresses", addresses)
                    .add("commentId", commentId)
                    .add("replicate", replicate)
                    .reprStringValues()
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TissueBlockContent that = (TissueBlockContent) o;
            return (Objects.equals(this.sourceBarcode, that.sourceBarcode)
                    && Objects.equals(this.sourceSampleId, that.sourceSampleId)
                    && Objects.equals(this.addresses, that.addresses)
                    && Objects.equals(this.commentId, that.commentId)
                    && Objects.equals(this.replicate, that.replicate)
            );
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceBarcode, sourceSampleId, addresses, commentId, replicate);
        }
    }
}