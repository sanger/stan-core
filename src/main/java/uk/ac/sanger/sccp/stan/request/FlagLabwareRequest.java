package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.LabwareFlag.Priority;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * Raise a flag on a piece of labware.
 * @author dr6
 */
public class FlagLabwareRequest {
    private String barcode;
    private String description;
    private String workNumber;
    private Priority priority;

    public FlagLabwareRequest(String barcode, String description, String workNumber, Priority priority) {
        this.barcode = barcode;
        this.description = description;
        this.workNumber = workNumber;
        this.priority = priority;
    }

    // required for framework
    public FlagLabwareRequest() {}

    /**
     * The barcode of the flagged labware.
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

    /** The work number (if any) to link to the flag. */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
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
        FlagLabwareRequest that = (FlagLabwareRequest) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.description, that.description)
                && Objects.equals(this.workNumber, that.workNumber)
                && this.priority == that.priority
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, description);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("FlagLabwareRequest")
                .add("barcode", barcode)
                .add("description", description)
                .addIfNotNull("workNumber", workNumber)
                .add("priority", priority)
                .reprStringValues()
                .toString();
    }
}