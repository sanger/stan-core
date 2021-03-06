package uk.ac.sanger.sccp.stan.request.register;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * A request to register labware (slides) containing samples (sections).
 * @author dr6
 */
public class SectionRegisterRequest {
    private List<SectionRegisterLabware> labware;

    public SectionRegisterRequest() {}

    public SectionRegisterRequest(Collection<SectionRegisterLabware> labware) {
        setLabware(labware);
    }

    public List<SectionRegisterLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(Collection<SectionRegisterLabware> labware) {
        this.labware = newArrayList(labware);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SectionRegisterRequest that = (SectionRegisterRequest) o;
        return Objects.equals(this.labware, that.labware);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labware);
    }

    @Override
    public String toString() {
        return "SectionRegisterRequest(" + labware + ")";
    }
}
