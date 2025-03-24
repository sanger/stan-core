package uk.ac.sanger.sccp.stan.request.history;

import uk.ac.sanger.sccp.stan.model.LabwareFlag.Priority;

import java.util.List;
import java.util.Objects;

/**
 * A flagged labware barcode and its flag priority
 * @author dr6
 */
public class FlagBarcodes {
    private final Priority priority;
    private final List<String> barcodes;

    public FlagBarcodes(Priority priority, List<String> barcodes) {
        this.priority = priority;
        this.barcodes = barcodes;
    }

    public Priority getPriority() {
        return this.priority;
    }

    public List<String> getBarcodes() {
        return this.barcodes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlagBarcodes that = (FlagBarcodes) o;
        return (this.priority == that.priority
                && Objects.equals(this.barcodes, that.barcodes));
    }

    @Override
    public int hashCode() {
        return Objects.hash(priority, barcodes);
    }

    @Override
    public String toString() {
        return String.format("(%s: %s)", priority, barcodes);
    }
}
