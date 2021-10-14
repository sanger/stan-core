package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Request to unrelease some labware.
 * @author dr6
 */
public class UnreleaseRequest {
    public static class UnreleaseLabware {
        private String barcode;
        private Integer highestSection;

        public UnreleaseLabware() {}

        public UnreleaseLabware(String barcode, Integer highestSection) {
            this.barcode = barcode;
            this.highestSection = highestSection;
        }

        public UnreleaseLabware(String barcode) {
            this(barcode, null);
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UnreleaseLabware that = (UnreleaseLabware) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.highestSection, that.highestSection));
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, highestSection);
        }

        @Override
        public String toString() {
            return highestSection == null ?
                    String.format("(barcode=%s)", repr(barcode)) :
                    String.format("(barcode=%s, highestSection=%s)", repr(barcode), highestSection);
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
