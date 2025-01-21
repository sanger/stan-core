package uk.ac.sanger.sccp.stan.request.history;

import uk.ac.sanger.sccp.stan.model.*;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
public class History {
    private List<HistoryEntry> entries;
    private List<Sample> samples;
    private List<Labware> labware;
    private Map<LabwareFlag.Priority, List<String>> flagPriorityBarcodes;

    public History(List<HistoryEntry> entries, List<Sample> samples, List<Labware> labware,
                   Map<LabwareFlag.Priority, List<String>> flagPriorityBarcodes) {
        setEntries(entries);
        setSamples(samples);
        setLabware(labware);
        setFlagPriorityBarcodes(flagPriorityBarcodes);
    }

    public History(List<HistoryEntry> entries, List<Sample> samples, List<Labware> labware) {
        this(entries, samples, labware, null);
    }

    public History() {
        this(null, null, null, null);
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

    public Map<LabwareFlag.Priority, List<String>> getFlagPriorityBarcodes() {
        return this.flagPriorityBarcodes;
    }

    public void setFlagPriorityBarcodes(Map<LabwareFlag.Priority, List<String>> flagPriorityBarcodes) {
        this.flagPriorityBarcodes = nullToEmpty(flagPriorityBarcodes);
    }

    /**
     * Gets flagged barcodes as a list pairing up a priority with a list of barcodes
     * @return a list of {@code FlagBarcodes} objects
     */
    public List<FlagBarcodes> getFlagBarcodes() {
        if (nullOrEmpty(flagPriorityBarcodes)) {
            return List.of();
        }
        return flagPriorityBarcodes.entrySet().stream()
                .map(e -> new FlagBarcodes(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        History that = (History) o;
        return (Objects.equals(this.entries, that.entries)
                && Objects.equals(this.samples, that.samples)
                && Objects.equals(this.labware, that.labware)
                && Objects.equals(this.flagPriorityBarcodes, that.flagPriorityBarcodes));
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries, samples, labware);
    }

    @Override
    public String toString() {
        return describe("History")
                .add("entries", entries)
                .add("samples", samples)
                .add("labware", labware)
                .add("flagPriorityBarcodes", flagPriorityBarcodes)
                .toString();
    }
}
