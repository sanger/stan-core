package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.Sample;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * @author dr6
 */
public class History {
    private List<HistoryEntry> entries;
    private List<Sample> samples;
    private List<Labware> labware;
    private List<String> flaggedBarcodes;

    public History(List<HistoryEntry> entries, List<Sample> samples, List<Labware> labware, List<String> flaggedBarcodes) {
        setEntries(entries);
        setSamples(samples);
        setLabware(labware);
        setFlaggedBarcodes(flaggedBarcodes);
    }

    public History(List<HistoryEntry> entries, List<Sample> samples, List<Labware> labware) {
        this(entries, samples, labware, null);
    }

    public History() {
        this(null, null, null);
    }

    public List<HistoryEntry> getEntries() {
        return this.entries;
    }

    public void setEntries(List<HistoryEntry> entries) {
        this.entries = nullToEmpty(entries);
    }

    public List<Sample> getSamples() {
        return this.samples;
    }

    public void setSamples(List<Sample> samples) {
        this.samples = nullToEmpty(samples);
    }

    public List<Labware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<Labware> labware) {
        this.labware = nullToEmpty(labware);
    }

    public List<String> getFlaggedBarcodes() {
        return this.flaggedBarcodes;
    }

    public void setFlaggedBarcodes(List<String> flaggedBarcodes) {
        this.flaggedBarcodes = nullToEmpty(flaggedBarcodes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        History that = (History) o;
        return (Objects.equals(this.entries, that.entries)
                && Objects.equals(this.samples, that.samples)
                && Objects.equals(this.labware, that.labware)
                && Objects.equals(this.flaggedBarcodes, that.flaggedBarcodes));
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
                .add("flaggedBarcodes", flaggedBarcodes)
                .toString();
    }
}
