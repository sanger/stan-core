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
    public static class WorkNumberData {
        private String workNumber;
        private Integer xeniumStudyId;
        private Integer ateraStudyId;

        public WorkNumberData(String workNumber, Integer xeniumStudyId, Integer ateraStudyId) {
            this.workNumber = workNumber;
            this.xeniumStudyId = xeniumStudyId;
            this.ateraStudyId = ateraStudyId;
        }

        public WorkNumberData() {
            this(null, null, null);
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

        public Integer getAteraStudyId() {
            return this.ateraStudyId;
        }

        public void setAteraStudyId(Integer ateraStudyId) {
            this.ateraStudyId = ateraStudyId;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            WorkNumberData that = (WorkNumberData) o;
            return (Objects.equals(this.workNumber, that.workNumber)
                    && Objects.equals(this.xeniumStudyId, that.xeniumStudyId)
                    && Objects.equals(this.ateraStudyId, that.ateraStudyId)
            );
        }

        @Override
        public int hashCode() {
            return Objects.hash(workNumber, xeniumStudyId, ateraStudyId);
        }

        @Override
        public String toString() {
            return String.format("(workNumber=%s, xeniumStudyId=%s, ateraStudyId=%s)",
                    workNumber, xeniumStudyId, ateraStudyId);
        }
    }
    private String barcode;
    private List<WorkNumberData> workNumberData = List.of();
    private List<String> probes = List.of();
    private boolean cellSegmentationRecorded;

    /** The barcode of the labware. */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /**
     * The work numbers and xenium study ids linked to the labware.
     * @deprecated use {@link #getWorkNumberData}
     **/
    @Deprecated
    public List<WorkNumberData> getWorkNumberXeniumStudyIds() {
        return getWorkNumberData();
    }

    public List<WorkNumberData> getWorkNumberData() {
        return this.workNumberData;
    }

    public void setWorkNumberData(List<WorkNumberData> workNumberData) {
        this.workNumberData = nullToEmpty(workNumberData);
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
                .add("workNumberData", workNumberData)
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
                && Objects.equals(this.workNumberData, that.workNumberData)
                && Objects.equals(this.probes, that.probes)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, workNumberData, probes, cellSegmentationRecorded);
    }
}