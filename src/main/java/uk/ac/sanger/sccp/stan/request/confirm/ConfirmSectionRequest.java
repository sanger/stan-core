package uk.ac.sanger.sccp.stan.request.confirm;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * A request to confirm and specify the sections of a planned operation
 * @author dr6
 */
public class ConfirmSectionRequest {
    private List<ConfirmSectionLabware> labware;

    public ConfirmSectionRequest(Iterable<ConfirmSectionLabware> labware) {
        setLabware(labware);
    }

    public ConfirmSectionRequest() {
        this(null);
    }

    public List<ConfirmSectionLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(Iterable<ConfirmSectionLabware> labware) {
        this.labware = newArrayList(labware);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmSectionRequest that = (ConfirmSectionRequest) o;
        return this.labware.equals(that.labware);
    }

    @Override
    public int hashCode() {
        return labware.hashCode();
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ConfirmSectionRequest")
                .add("labware", labware)
                .toString();
    }
}
