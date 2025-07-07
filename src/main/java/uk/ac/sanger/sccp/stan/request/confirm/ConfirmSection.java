package uk.ac.sanger.sccp.stan.request.confirm;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.coalesce;

/**
 * The information about a particular section in a confirmed operation
 * @author dr6
 */
public class ConfirmSection {
    private Address destinationAddress;
    private Integer sampleId;
    private Integer newSection;
    private List<Integer> commentIds = List.of();
    private String region;
    private String thickness;

    public ConfirmSection() {}

    public ConfirmSection(Address destinationAddress, Integer sampleId, Integer newSection,
                          List<Integer> commentIds, String region) {
        this.destinationAddress = destinationAddress;
        this.sampleId = sampleId;
        this.newSection = newSection;
        setCommentIds(commentIds);
        this.region = region;
    }

    public ConfirmSection(Address destinationAddress, Integer sampleId, Integer newSection) {
        this(destinationAddress, sampleId, newSection, null, null);
    }

    public Address getDestinationAddress() {
        return this.destinationAddress;
    }

    public void setDestinationAddress(Address destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public Integer getNewSection() {
        return this.newSection;
    }

    public void setNewSection(Integer newSection) {
        this.newSection = newSection;
    }

    public List<Integer> getCommentIds() {
        return this.commentIds;
    }

    public void setCommentIds(List<Integer> commentIds) {
        this.commentIds = coalesce(commentIds, List.of());
    }

    public String getRegion() {
        return this.region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getThickness() {
        return this.thickness;
    }

    public void setThickness(String thickness) {
        this.thickness = thickness;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmSection that = (ConfirmSection) o;
        return (Objects.equals(this.destinationAddress, that.destinationAddress)
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.newSection, that.newSection)
                && Objects.equals(this.commentIds, that.commentIds)
                && Objects.equals(this.region, that.region)
                && Objects.equals(this.thickness, that.thickness)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinationAddress, sampleId, newSection);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ConfirmSection")
                .add("destinationAddress", destinationAddress)
                .add("sampleId", sampleId)
                .add("newSection", newSection)
                .add("commentIds", commentIds)
                .addRepr("region", region)
                .addRepr("thickness", thickness)
                .toString();
    }
}
