package uk.ac.sanger.sccp.stan.request.register;

import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.Tissue;

import java.util.List;
import java.util.Objects;

/**
 * Information about a tissue name clash
 * @author dr6
 */
public class RegisterClash {
    private Tissue tissue;
    private List<Labware> labware;

    public RegisterClash() {}

    public RegisterClash(Tissue tissue, List<Labware> labware) {
        this.tissue = tissue;
        this.labware = labware;
    }

    public Tissue getTissue() {
        return this.tissue;
    }

    public void setTissue(Tissue tissue) {
        this.tissue = tissue;
    }

    public List<Labware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<Labware> labware) {
        this.labware = labware;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisterClash that = (RegisterClash) o;
        return (Objects.equals(this.tissue, that.tissue)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(tissue, labware);
    }

    @Override
    public String toString() {
        return String.format("(tissue=%s, labware=%s)", tissue, labware);
    }
}
