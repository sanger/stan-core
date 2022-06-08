package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * A request to transfer original sample into pots.
 * @author dr6
 */
public class PotProcessingRequest {
    private String sourceBarcode;
    private String workNumber;
    private List<PotProcessingDestination> destinations = List.of();
    private boolean sourceDiscarded = false;

    public PotProcessingRequest() {}

    public PotProcessingRequest(String sourceBarcode, String workNumber, List<PotProcessingDestination> destinations,
                                boolean sourceDiscarded) {
        setSourceBarcode(sourceBarcode);
        setWorkNumber(workNumber);
        setDestinations(destinations);
        setSourceDiscarded(sourceDiscarded);
    }

    public PotProcessingRequest(String sourceBarcode, String workNumber, List<PotProcessingDestination> destinations) {
        this(sourceBarcode, workNumber, destinations, false);
    }

    /**
     * The source barcode.
     */
    public String getSourceBarcode() {
        return this.sourceBarcode;
    }

    public void setSourceBarcode(String sourceBarcode) {
        this.sourceBarcode = sourceBarcode;
    }

    /**
     * The work number.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /**
     * The destinations that will be created.
     */
    public List<PotProcessingDestination> getDestinations() {
        return this.destinations;
    }

    public void setDestinations(List<PotProcessingDestination> destinations) {
        this.destinations = (destinations==null ? List.of() : destinations);
    }

    /**
     * Is the source labware discarded?
     */
    public boolean isSourceDiscarded() {
        return this.sourceDiscarded;
    }

    public void setSourceDiscarded(boolean sourceDiscarded) {
        this.sourceDiscarded = sourceDiscarded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PotProcessingRequest that = (PotProcessingRequest) o;
        return (Objects.equals(this.sourceBarcode, that.sourceBarcode)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.destinations, that.destinations)
                && this.sourceDiscarded==that.sourceDiscarded
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceBarcode, workNumber, destinations);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("PotProcessingRequest")
                .add("sourceBarcode", sourceBarcode)
                .add("workNumber", workNumber)
                .add("destinations", destinations)
                .add("sourceDiscarded", sourceDiscarded)
                .reprStringValues()
                .toString();
    }

    /**
     * A destination for pot processing.
     * @author dr6
     */
    public static class PotProcessingDestination {
        private String labwareType;
        private String fixative;
        private Integer commentId;

        public PotProcessingDestination() {}

        public PotProcessingDestination(String labwareType, String fixative, Integer commentId) {
            this.labwareType = labwareType;
            this.fixative = fixative;
            this.commentId = commentId;
        }

        public PotProcessingDestination(String labwareType, String fixative) {
            this(labwareType, fixative, null);
        }

        /**
         * The name of the type of labware.
         */
        public String getLabwareType() {
            return this.labwareType;
        }

        public void setLabwareType(String labwareType) {
            this.labwareType = labwareType;
        }

        /**
         * The fixative (if any).
         */
        public String getFixative() {
            return this.fixative;
        }

        public void setFixative(String fixative) {
            this.fixative = fixative;
        }

        /**
         * The id of the comment to record, if any.
         */
        public Integer getCommentId() {
            return this.commentId;
        }

        public void setCommentId(Integer commentId) {
            this.commentId = commentId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PotProcessingDestination that = (PotProcessingDestination) o;
            return (Objects.equals(this.labwareType, that.labwareType)
                    && Objects.equals(this.fixative, that.fixative)
                    && Objects.equals(this.commentId, that.commentId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(labwareType, fixative, commentId);
        }

        @Override
        public String toString() {
            return BasicUtils.describe("PotProcessingDestination")
                    .add("labwareType", labwareType)
                    .add("fixative", fixative)
                    .add("commentId", commentId)
                    .reprStringValues()
                    .toString();
        }
    }
}