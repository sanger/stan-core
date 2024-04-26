package uk.ac.sanger.sccp.stan.request.stain;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * A request to perform a stain operation
 * @author dr6
 */
public class StainRequest {
    private String stainType;
    private List<String> barcodes;
    private List<TimeMeasurement> timeMeasurements = List.of();
    private String workNumber;
    private List<Integer> commentIds = List.of();

    public StainRequest() {}

    public StainRequest(String stainType, List<String> barcodes, List<TimeMeasurement> timeMeasurements,
                        List<Integer> commentIds) {
        this.stainType = stainType;
        this.barcodes = barcodes;
        setTimeMeasurements(timeMeasurements);
        setCommentIds(commentIds);
    }

    public String getStainType() {
        return this.stainType;
    }

    public void setStainType(String stainType) {
        this.stainType = stainType;
    }

    public List<String> getBarcodes() {
        return this.barcodes;
    }

    public void setBarcodes(List<String> barcodes) {
        this.barcodes = barcodes;
    }

    public List<TimeMeasurement> getTimeMeasurements() {
        return this.timeMeasurements;
    }

    public void setTimeMeasurements(List<TimeMeasurement> timeMeasurements) {
        this.timeMeasurements = nullToEmpty(timeMeasurements);
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    public List<Integer> getCommentIds() {
        return this.commentIds;
    }

    public void setCommentIds(List<Integer> commentIds) {
        this.commentIds = nullToEmpty(commentIds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StainRequest that = (StainRequest) o;
        return (Objects.equals(this.stainType, that.stainType)
                && Objects.equals(this.barcodes, that.barcodes)
                && Objects.equals(this.timeMeasurements, that.timeMeasurements)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.commentIds, that.commentIds)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(stainType, barcodes, timeMeasurements, workNumber, commentIds);
    }

    @Override
    public String toString() {
        return describe(this)
                .add("stainType", stainType)
                .add("barcodes", barcodes)
                .add("timeMeasurements", timeMeasurements)
                .add("workNumber", workNumber)
                .add("commentIds", commentIds)
                .toString();
    }
}
