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
    public static class WorkNumberXeniumStudyId {
        private String workNumber;
        private Integer xeniumStudyId;

        public WorkNumberXeniumStudyId(String workNumber, Integer xeniumStudyId) {
            this.workNumber = workNumber;
            this.xeniumStudyId = xeniumStudyId;
        }

        public WorkNumberXeniumStudyId() {
            this(null, null);
        }

        public String getWorkNumber() {
            return this.workNumber;
        }

        public void setWorkNumber(String workNumber) {
            this.workNumber = workNumber;
        }

        public Integer getXeniumStudyId() {
            return this.xeniumStudyId;
        }

        public void setXeniumStudyId(Integer xeniumStudyId) {
            this.xeniumStudyId = xeniumStudyId;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            WorkNumberXeniumStudyId that = (WorkNumberXeniumStudyId) o;
            return (Objects.equals(this.workNumber, that.workNumber)
                    && Objects.equals(this.xeniumStudyId, that.xeniumStudyId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(workNumber, xeniumStudyId);
        }

        @Override
        public String toString() {
            return String.format("(workNumber=%s, xeniumStudyId=%s)", workNumber, xeniumStudyId);
        }
    }
    private String barcode;
    private List<WorkNumberXeniumStudyId> workNumberXeniumStudyIds = List.of();
    private List<String> probes = List.of();
    private boolean cellSegmentationRecorded;

    /** The barcode of the labware. */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /** The work numbers and xenium study ids linked to the labware. */
    public List<WorkNumberXeniumStudyId> getWorkNumberXeniumStudyIds() {
        return this.workNumberXeniumStudyIds;
    }

    public void setWorkNumberXeniumStudyIds(List<WorkNumberXeniumStudyId> workNumberXeniumStudyIds) {
        this.workNumberXeniumStudyIds = nullToEmpty(workNumberXeniumStudyIds);
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
                .add("workNumberXeniumStudyIds", workNumberXeniumStudyIds)
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
                && Objects.equals(this.workNumberXeniumStudyIds, that.workNumberXeniumStudyIds)
                && Objects.equals(this.probes, that.probes)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, workNumberXeniumStudyIds, probes, cellSegmentationRecorded);
    }
}