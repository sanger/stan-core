package uk.ac.sanger.sccp.stan.request;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * Information to show when a user scans in labware for the analyser op.
 * @author dr6
 */
public class AnalyserScanData {
    private String barcode;
    private List<String> workNumbers = List.of();
    private List<String> probes = List.of();
    private boolean cellSegmentationRecorded;
//
//    // Deserialisation constructor
//    public AnalyserScanData() {}
//
//    public AnalyserScanData(String barcode, List<String> workNumbers, List<String> probes, boolean cellSegmentationRecorded) {
//        setBarcode(barcode);
//        setWorkNumbers(workNumbers);
//        setProbes(probes);
//        setCellSegmentationRecorded(cellSegmentationRecorded);
//    }

    /** The barcode of the labware. */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /** The work numbers linked to the labware. */
    public List<String> getWorkNumbers() {
        return this.workNumbers;
    }

    public void setWorkNumbers(List<String> workNumbers) {
        this.workNumbers = nullToEmpty(workNumbers);
    }

    /** The names of probes recorded on the labware. */
    public List<String> getProbes() {
        return this.probes;
    }

    public void setProbes(List<String> probes) {
        this.probes = nullToEmpty(probes);
    }

    /** Has cell segmentation been recorded? */
    public boolean isCellSegmentationRecorded() {
        return this.cellSegmentationRecorded;
    }

    public void setCellSegmentationRecorded(boolean cellSegmentationRecorded) {
        this.cellSegmentationRecorded = cellSegmentationRecorded;
    }

    @Override
    public String toString() {
        return describe(this)
                .add("barcode", barcode)
                .add("workNumbers", workNumbers)
                .add("probes", probes)
                .add("cellSegmentationRecorded", cellSegmentationRecorded)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != this.getClass()) return false;
        AnalyserScanData that = (AnalyserScanData) o;
        return (this.cellSegmentationRecorded == that.cellSegmentationRecorded
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.workNumbers, that.workNumbers)
                && Objects.equals(this.probes, that.probes)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, workNumbers, probes, cellSegmentationRecorded);
    }
}