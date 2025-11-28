package uk.ac.sanger.sccp.stan.request.confirm;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.coalesce;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * The information about a particular section in a confirmed operation
 * @author dr6
 */
public class ConfirmSection {
    private List<Address> destinationAddresses = List.of();
    private Integer sampleId;
    private Integer newSection;
    private List<Integer> commentIds = List.of();
    private String thickness;

    public ConfirmSection() {}

    public ConfirmSection(List<Address> destinationAddresses, Integer sampleId, Integer newSection,
                          List<Integer> commentIds) {
        setDestinationAddress(destinationAddresses);
        setSampleId(sampleId);
        setNewSection(newSection);
        setCommentIds(commentIds);
    }

    public ConfirmSection(Address destinationAddress, Integer sampleId, Integer newSection,
                          List<Integer> commentIds) {
        this(destinationAddress==null ? null : List.of(destinationAddress), sampleId, newSection, commentIds);
    }

    public ConfirmSection(Address destinationAddress, Integer sampleId, Integer newSection) {
        this(destinationAddress, sampleId, newSection, null);
    }

    public List<Address> getDestinationAddresses() {
        return this.destinationAddresses;
    }

    public void setDestinationAddress(List<Address> destinationAddresses) {
        this.destinationAddresses = nullToEmpty(destinationAddresses);
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
        return (Objects.equals(this.destinationAddresses, that.destinationAddresses)
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.newSection, that.newSection)
                && Objects.equals(this.commentIds, that.commentIds)
                && Objects.equals(this.thickness, that.thickness)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinationAddresses, sampleId, newSection);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ConfirmSection")
                .add("destinationAddresses", destinationAddresses)
                .add("sampleId", sampleId)
                .add("newSection", newSection)
                .add("commentIds", commentIds)
                .addRepr("thickness", thickness)
                .toString();
    }
}
