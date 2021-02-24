package uk.ac.sanger.sccp.stan.request.register;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.Tissue;

import java.util.List;
import java.util.Objects;

import static java.util.Collections.emptyList;

/**
 * @author dr6
 */
public class RegisterResult {
    private List<Labware> labware;
    private List<Tissue> tissue;

    public RegisterResult() {
        this(emptyList(), emptyList());
    }

    public RegisterResult(List<Labware> labware, List<Tissue> tissue) {
        this.labware = labware;
        this.tissue = tissue;
    }

    public List<Labware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<Labware> labware) {
        this.labware = labware;
    }

    public List<Tissue> getTissue() {
        return this.tissue;
    }

    public void setTissue(List<Tissue> tissue) {
        this.tissue = tissue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisterResult that = (RegisterResult) o;
        return (Objects.equals(this.labware, that.labware)
                && Objects.equals(this.tissue, that.tissue));
    }

    @Override
    public int hashCode() {
        return Objects.hash(labware, tissue);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("labware", labware)
                .add("tissue", tissue)
                .toString();
    }
}
