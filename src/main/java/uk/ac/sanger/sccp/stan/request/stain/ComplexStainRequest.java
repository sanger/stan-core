package uk.ac.sanger.sccp.stan.request.stain;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

/**
 * Request to record a stain of a complex nature
 * @author dr6
 */
public class ComplexStainRequest {
    private List<String> stainTypes;
    private List<ComplexStainLabware> labware;

    public ComplexStainRequest() {
        this(null, null);
    }

    public ComplexStainRequest(List<String> stainTypes, List<ComplexStainLabware> labware) {
        setStainTypes(stainTypes);
        setLabware(labware);
    }

    public List<String> getStainTypes() {
        return this.stainTypes;
    }

    public void setStainTypes(List<String> stainTypes) {
        this.stainTypes = (stainTypes==null ? List.of() : stainTypes);
    }

    public List<ComplexStainLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<ComplexStainLabware> labware) {
        this.labware = (labware==null ? new ArrayList<>() : labware);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComplexStainRequest that = (ComplexStainRequest) o;
        return (Objects.equals(this.stainTypes, that.stainTypes)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(stainTypes, labware);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ComplexStainRequest")
                .add("stainType", stainTypes)
                .add("labware", labware)
                .toString();
    }
}
