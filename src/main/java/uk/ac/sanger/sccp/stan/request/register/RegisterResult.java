package uk.ac.sanger.sccp.stan.request.register;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.Labware;

import java.util.List;
import java.util.Objects;

/**
 * @author dr6
 */
public class RegisterResult {
    private List<Labware> labware = List.of();
    private List<RegisterClash> clashes = List.of();

    public RegisterResult() {}

    public RegisterResult(List<Labware> labware) {
        this.labware = labware;
    }

    public List<Labware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<Labware> labware) {
        this.labware = labware;
    }

    public List<RegisterClash> getClashes() {
        return this.clashes;
    }

    public void setClashes(List<RegisterClash> clashes) {
        this.clashes = clashes;
    }

    public static RegisterResult clashes(List<RegisterClash> clashes) {
        RegisterResult r = new RegisterResult();
        r.setClashes(clashes);
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisterResult that = (RegisterResult) o;
        return (Objects.equals(this.labware, that.labware)
                && Objects.equals(this.clashes, that.clashes));
    }

    @Override
    public int hashCode() {
        return Objects.hash(labware, clashes);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("labware", labware)
                .add("clashes", clashes)
                .omitNullValues()
                .toString();
    }
}
