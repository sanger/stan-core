package uk.ac.sanger.sccp.stan.request;

import org.jetbrains.annotations.NotNull;
import uk.ac.sanger.sccp.stan.model.*;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Labware with an additional field specifying whether it is flagged.
 * @author dr6
 */
public class LabwareFlagged {
    @NotNull
    private final Labware labware;
    private final boolean flagged;

    public LabwareFlagged(Labware labware, boolean flagged) {
        this.labware = requireNonNull(labware, "labware is null");
        this.flagged = flagged;
    }

    /**
     * The unique id of this labware.
     */
    public Integer getId() {
        return this.labware.getId();
    }

    /**
     * The unique barcode of this labware.
     */
    public String getBarcode() {
        return this.labware.getBarcode();
    }

    /**
     * The external barcode of this labware, as input by the user.
     */
    public String getExternalBarcode() {
        return this.labware.getExternalBarcode();
    }

    /**
     * The type of labware.
     */
    public LabwareType getLabwareType() {
        return this.labware.getLabwareType();
    }

    /**
     * The slots in this labware. The number of slots and their addresses are determined by the labware type.
     */
    public List<Slot> getSlots() {
        return this.labware.getSlots();
    }

    /**
     * Has this labware been released?
     */
    public boolean isReleased() {
        return this.labware.isReleased();
    }

    /**
     * Has this labware been destroyed?
     */
    public boolean isDestroyed() {
        return this.labware.isDestroyed();
    }

    /**
     * Has this labware been discarded?
     */
    public boolean isDiscarded() {
        return this.labware.isDiscarded();
    }

    /**
     * Has this labware been marked as used?
     */
    public boolean isUsed() {
        return this.labware.isUsed();
    }

    /**
     * The state, derived from the contents and other fields on the labware.
     */
    public Labware.State getState() {
        return this.labware.getState();
    }

    /**
     * The time when this labware was created in the application.
     */
    public LocalDateTime getCreated() {
        return this.labware.getCreated();
    }

    @NotNull
    public Labware getLabware() {
        return this.labware;
    }

    /**
     * Is there a labware flag applicable to this labware?
     */
    public boolean isFlagged() {
        return this.flagged;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabwareFlagged that = (LabwareFlagged) o;
        return (this.flagged == that.flagged
                && this.labware.equals(that.labware));
    }

    @Override
    public int hashCode() {
        return this.labware.hashCode();
    }

    @Override
    public String toString() {
        return String.format("LabwareFlagged(%s, %s)", labware.getBarcode(), flagged);
    }
}