package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.Sample;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * @author dr6
 */
public class History {
    private List<HistoryEntry> entries;
    private List<Sample> samples;
    private List<Labware> labware;
    public History(List<HistoryEntry> entries, List<Sample> samples, List<Labware> labware) {
        setEntries(entries);
        setSamples(samples);
        setLabware(labware);
    }

    public History() {}

    public List<HistoryEntry> getEntries() {
        return this.entries;
    }

    public void setEntries(List<HistoryEntry> entries) {
        this.entries = entries;
    }

    public List<Sample> getSamples() {
        return this.samples;
    }

    public void setSamples(List<Sample> samples) {
        this.samples = samples;
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
        History that = (History) o;
        return (Objects.equals(this.entries, that.entries)
                && Objects.equals(this.samples, that.samples)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries, samples, labware);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("History")
                .add("entries", entries)
                .add("samples", samples)
                .add("labware", labware)
                .toString();
    }
}
