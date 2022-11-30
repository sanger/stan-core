package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.*;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * A request to record results
 * @author dr6
 */
public class ResultRequest {
    public static class SampleResult {
        private Address address;
        private PassFail result;
        private Integer commentId;

        public SampleResult() {}

        public SampleResult(Address address, PassFail result, Integer commentId) {
            this.address = address;
            this.result = result;
            this.commentId = commentId;
        }

        public Address getAddress() {
            return this.address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        public PassFail getResult() {
            return this.result;
        }

        public void setResult(PassFail result) {
            this.result = result;
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
            SampleResult that = (SampleResult) o;
            return (Objects.equals(this.address, that.address)
                    && this.result == that.result
                    && Objects.equals(this.commentId, that.commentId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, result, commentId);
        }

        @Override
        public String toString() {
            return describe("SampleResult")
                    .add("address", address)
                    .add("result", result)
                    .add("commentId", commentId)
                    .toString();
        }
    }

    public static class LabwareResult {
        private String barcode;
        private List<SampleResult> sampleResults;
        private List<SlotMeasurementRequest> slotMeasurements;
        private SlideCosting costing;
        private String reagentLot;

        public LabwareResult() {
            this(null, null, null, null, null);
        }

        public LabwareResult(String barcode) {
            this(barcode, null, null, null, null);
        }

        public LabwareResult(String barcode, List<SampleResult> sampleResults,
                             List<SlotMeasurementRequest> slotMeasurements, SlideCosting costing, String reagentLot) {
            this.barcode = barcode;
            setSampleResults(sampleResults);
            setSlotMeasurements(slotMeasurements);
            setCosting(costing);
            setReagentLot(reagentLot);
        }

        public String getBarcode() {
            return this.barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        public List<SampleResult> getSampleResults() {
            return this.sampleResults;
        }

        public void setSampleResults(List<SampleResult> sampleResults) {
            this.sampleResults = sampleResults==null ? List.of() : sampleResults;
        }

        public List<SlotMeasurementRequest> getSlotMeasurements() {
            return this.slotMeasurements;
        }

        public void setSlotMeasurements(List<SlotMeasurementRequest> slotMeasurements) {
            this.slotMeasurements = slotMeasurements==null ? List.of() : slotMeasurements;
        }

        /* An optional costing for the labware. */
        public SlideCosting getCosting() {
            return this.costing;
        }

        public void setCosting(SlideCosting costing) {
            this.costing = costing;
        }

        /** An optional reagent lot number for the labware. */
        public String getReagentLot() {
            return this.reagentLot;
        }

        public void setReagentLot(String reagentLot) {
            this.reagentLot = reagentLot;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LabwareResult that = (LabwareResult) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.sampleResults, that.sampleResults)
                    && Objects.equals(this.slotMeasurements, that.slotMeasurements)
                    && this.costing == that.costing
                    && Objects.equals(this.reagentLot, that.reagentLot));
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, sampleResults, slotMeasurements, costing, reagentLot);
        }

        @Override
        public String toString() {
            return describe("LabwareResult")
                    .addRepr("barcode", barcode)
                    .add("sampleResults", sampleResults)
                    .add("slotMeasurements", slotMeasurements)
                    .add("costing", costing)
                    .addRepr("reagentLot", reagentLot)
                    .toString();
        }
    }

    private String operationType;
    private List<LabwareResult> labwareResults;
    private String workNumber;

    public List<LabwareResult> getLabwareResults() {
        return this.labwareResults;
    }

    public void setLabwareResults(List<LabwareResult> labwareResults) {
        this.labwareResults = labwareResults;
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResultRequest that = (ResultRequest) o;
        return (Objects.equals(this.labwareResults, that.labwareResults)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.operationType, that.operationType));
    }

    @Override
    public int hashCode() {
        return Objects.hash(labwareResults, workNumber, operationType);
    }

    @Override
    public String toString() {
        return describe("ResultRequest")
                .add("labwareResults", labwareResults)
                .addRepr("workNumber", workNumber)
                .addRepr("operationType", operationType)
                .toString();
    }
}
