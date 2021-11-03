package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.PassFail;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * The information returned from an extract result query
 * @author dr6
 */
public class ExtractResult {
    private Labware labware;
    private PassFail result;
    private String concentration;

    public ExtractResult() {}

    public ExtractResult(Labware labware, PassFail result, String concentration) {
        this.labware = labware;
        this.result = result;
        this.concentration = concentration;
    }

    public Labware getLabware() {
        return this.labware;
    }

    public void setLabware(Labware labware) {
        this.labware = labware;
    }

    public PassFail getResult() {
        return this.result;
    }

    public void setResult(PassFail result) {
        this.result = result;
    }

    public String getConcentration() {
        return this.concentration;
    }

    public void setConcentration(String concentration) {
        this.concentration = concentration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtractResult that = (ExtractResult) o;
        return (Objects.equals(this.labware, that.labware)
                && this.result == that.result
                && Objects.equals(this.concentration, that.concentration));
    }

    @Override
    public int hashCode() {
        return (labware!=null ? labware.hashCode() : 0);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("labware", (labware==null ? null : labware.getBarcode()))
                .add("result", result)
                .add("concentration", concentration)
                .toString();
    }
}
