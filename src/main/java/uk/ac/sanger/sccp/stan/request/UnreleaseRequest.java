package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

/**
 * Request to unrelease some labware.
 * @author dr6
 */
public class UnreleaseRequest {
    public static class UnreleaseLabware {
        private String barcode;
        private Integer highestSection;
        private String workNumber;

        public UnreleaseLabware() {}

        public UnreleaseLabware(String barcode, Integer highestSection, String workNumber) {
            this.barcode = barcode;
            this.highestSection = highestSection;
            this.workNumber = workNumber;
        }

        public UnreleaseLabware(String barcode) {
            this(barcode, null, null);
        }

        public String getBarcode() {
            return this.barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        public Integer getHighestSection() {
            return this.highestSection;
        }

        public void setHighestSection(Integer highestSection) {
            this.highestSection = highestSection;
        }

        public String getWorkNumber() {
            return this.workNumber;
        }

        public void setWorkNumber(String workNumber) {
            this.workNumber = workNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UnreleaseLabware that = (UnreleaseLabware) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.highestSection, that.highestSection)
                    && Objects.equals(this.workNumber, that.workNumber));
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, highestSection, workNumber);
        }

        @Override
        public String toString() {
            return BasicUtils.describe("")
                    .add("barcode", barcode)
                    .addIfNotNull("highestSection", highestSection)
                    .addIfNotNull("workNumber", workNumber)
                    .reprStringValues()
                    .toString();
        }
    }

    private List<UnreleaseLabware> labware;

    public UnreleaseRequest() {
        this(null);
    }

    public UnreleaseRequest(List<UnreleaseLabware> labware) {
        setLabware(labware);
    }

    public List<UnreleaseLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<UnreleaseLabware> labware) {
        this.labware = (labware==null ? new ArrayList<>() : labware);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnreleaseRequest that = (UnreleaseRequest) o;
        return Objects.equals(this.labware, that.labware);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(labware);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("UnreleaseRequest")
                .add("labware", labware)
                .toString();
    }
}
