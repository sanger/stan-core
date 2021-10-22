package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.PassFail;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

/**
 * A request to record a result for extract operations
 * @author dr6
 */
public class ExtractResultRequest {
    public static class ExtractResultLabware {
        private String barcode;
        private PassFail result;
        private String concentration;
        private Integer commentId;

        public ExtractResultLabware() {}

        public ExtractResultLabware(String barcode, PassFail result, String concentration, Integer commentId) {
            this.barcode = barcode;
            this.result = result;
            this.concentration = concentration;
            this.commentId = commentId;
        }

        public ExtractResultLabware(String barcode, PassFail result, String concentration) {
            this(barcode, result, concentration, null);
        }

        public ExtractResultLabware(String barcode, PassFail result, Integer commentId) {
            this(barcode, result, null, commentId);
        }

        public String getBarcode() {
            return this.barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        public PassFail getResult() {
            return this.result;
        }

        public void setResult(PassFail result) {
            this.result = result;
        }

        public String getConcentration() {
            return this.concentration;
        }

        public void setConcentration(String concentration) {
            this.concentration = concentration;
        }

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
            ExtractResultLabware that = (ExtractResultLabware) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && this.result == that.result
                    && Objects.equals(this.concentration, that.concentration)
                    && Objects.equals(this.commentId, that.commentId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, result, concentration, commentId);
        }

        @Override
        public String toString() {
            return BasicUtils.describe(this)
                    .addRepr("barcode", barcode)
                    .add("result", result)
                    .addRepr("concentration", concentration)
                    .add("commentId", commentId)
                    .toString();
        }
    }

    private List<ExtractResultLabware> labware;
    private String workNumber;

    public ExtractResultRequest() {
        this(null, null);
    }

    public ExtractResultRequest(List<ExtractResultLabware> labware, String workNumber) {
        setLabware(labware);
        this.workNumber = workNumber;
    }

    public List<ExtractResultLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<ExtractResultLabware> labware) {
        this.labware = (labware==null ? new ArrayList<>() : labware);
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtractResultRequest that = (ExtractResultRequest) o;
        return (Objects.equals(this.labware, that.labware)
                && Objects.equals(this.workNumber, that.workNumber));
    }

    @Override
    public int hashCode() {
        return Objects.hash(labware, workNumber);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ExtractResultRequest")
                .add("labware", labware)
                .addRepr("workNumber", workNumber)
                .toString();
    }
}
