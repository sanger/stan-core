package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Destruction;

import java.util.List;
import java.util.Objects;

/**
 * The result of a destroy request
 * @author dr6
 */
public class DestroyResult {
    private List<Destruction> destructions;

    public DestroyResult() {}

    public DestroyResult(List<Destruction> destructions) {
        this.destructions = destructions;
    }

    public List<Destruction> getDestructions() {
        return this.destructions;
    }

    public void setDestructions(List<Destruction> destructions) {
        this.destructions = destructions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DestroyResult that = (DestroyResult) o;
        return Objects.equals(this.destructions, that.destructions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(destructions);
    }

    @Override
    public String toString() {
        return String.format("DestroyResult(%s)", destructions);
    }
}
