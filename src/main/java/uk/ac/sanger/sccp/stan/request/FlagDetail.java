package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.LabwareFlag.Priority;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Information about flags related to some labware.
 * @author dr6
 */
public class FlagDetail {
    /**
     * Summary of a particular labware flag.
     * @author dr6
     */
    public static class FlagSummary {
        private String barcode;
        private String description;
        private Priority priority;

        public FlagSummary(String barcode, String description, Priority priority) {
            this.barcode = barcode;
            this.description = description;
            this.priority = priority;
        }

        /**
         * The labware barcode that was flagged.
         */
        public String getBarcode() {
            return this.barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        /**
         * The description of the flag.
         */
        public String getDescription() {
            return this.description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Priority getPriority() {
            return this.priority;
        }

        public void setPriority(Priority priority) {
            this.priority = priority;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FlagSummary that = (FlagSummary) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.description, that.description)
                    && this.priority == that.priority
            );
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, description);
        }

        @Override
        public String toString() {
            return String.format("[%s: %s: %s]", priority, barcode, repr(description));
        }
    }

    private String barcode;
    private List<FlagSummary> flags;

    public FlagDetail(String barcode, List<FlagSummary> flags) {
        this.barcode = barcode;
        setFlags(flags);
    }

    /**
     * The barcode of the labware whose flags have been looked up.
     */
    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    /**
     * Summaries of the flags applicable to the specified labware.
     */
    public List<FlagSummary> getFlags() {
        return this.flags;
    }

    public void setFlags(List<FlagSummary> flags) {
        this.flags = nullToEmpty(flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlagDetail that = (FlagDetail) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.flags, that.flags));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, flags);
    }

    @Override
    public String toString() {
        return describe("FlagDetail")
                .add("barcode", barcode)
                .add("flags", flags)
                .toString();
    }
}