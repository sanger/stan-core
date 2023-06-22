package uk.ac.sanger.sccp.stan.request.register;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A request to register labware (slides) containing samples (sections).
 * @author dr6
 */
public class SectionRegisterRequest {
    private List<SectionRegisterLabware> labware;
    private String workNumber;

    public SectionRegisterRequest() {}

    public SectionRegisterRequest(Collection<SectionRegisterLabware> labware, String workNumber) {
        setLabware(labware);
        setWorkNumber(workNumber);
    }

    public List<SectionRegisterLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(Collection<SectionRegisterLabware> labware) {
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
        SectionRegisterRequest that = (SectionRegisterRequest) o;
        return Objects.equals(this.labware, that.labware) && Objects.equals(this.workNumber, that.workNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labware, workNumber);
    }

    @Override
    public String toString() {
        return "SectionRegisterRequest(" + labware + ", workNumber="+repr(workNumber)+")";
    }
}
