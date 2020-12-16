package uk.ac.sanger.sccp.stan.service.label;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.LabelType;

import java.util.List;
import java.util.Objects;

/**
 * @author dr6
 */
public class LabelPrintRequest {
    private final LabelType labelType;
    private final List<LabwareLabelData> labwareLabelData;

    public LabelPrintRequest(LabelType labelType, List<LabwareLabelData> labwareLabelData) {
        this.labelType = labelType;
        this.labwareLabelData = List.copyOf(labwareLabelData);
    }

    public List<LabwareLabelData> getLabwareLabelData() {
        return this.labwareLabelData;
    }

    public LabelType getLabelType() {
        return this.labelType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabelPrintRequest that = (LabelPrintRequest) o;
        return (Objects.equals(this.labwareLabelData, that.labwareLabelData)
                && Objects.equals(this.labelType, that.labelType));
    }

    @Override
    public int hashCode() {
        return Objects.hash(labwareLabelData);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("labelType", labelType)
                .add("labwareLabelData", labwareLabelData)
                .toString();
    }
}
