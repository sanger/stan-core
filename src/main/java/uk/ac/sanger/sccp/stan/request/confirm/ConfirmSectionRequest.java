package uk.ac.sanger.sccp.stan.request.confirm;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * A request to confirm and specify the sections of a planned operation
 * @author dr6
 */
public class ConfirmSectionRequest {
    private List<ConfirmSectionLabware> labware;
    private String workNumber;

    public ConfirmSectionRequest(Iterable<ConfirmSectionLabware> labware, String workNumber) {
        setLabware(labware);
        this.workNumber = workNumber;
    }

    public ConfirmSectionRequest(Iterable<ConfirmSectionLabware> labware) {
        this(labware, null);
    }

    public ConfirmSectionRequest() {
        this(null, null);
    }

    public List<ConfirmSectionLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(Iterable<ConfirmSectionLabware> labware) {
        this.labware = newArrayList(labware);
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
        ConfirmSectionRequest that = (ConfirmSectionRequest) o;
        return (this.labware.equals(that.labware) && Objects.equals(this.workNumber, that.workNumber));
    }

    @Override
    public int hashCode() {
        return labware.hashCode();
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ConfirmSectionRequest")
                .add("labware", labware)
                .addRepr("workNumber", workNumber)
                .toString();
    }
}
