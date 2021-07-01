package uk.ac.sanger.sccp.stan.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Entity
public class Labware {

    public enum State {
        empty, active, discarded, released, destroyed
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String barcode;
    private String externalBarcode;

    @ManyToOne
    private LabwareType labwareType;

    @OneToMany
    @JoinColumn(name="labware_id")
    @OrderBy("address.row, address.column")
    private List<Slot> slots;

    private boolean discarded;
    private boolean released;
    private boolean destroyed;

    @Generated(GenerationTime.INSERT)
    private LocalDateTime created;

    public Labware() {}

    public Labware(Integer id, String barcode, LabwareType labwareType, List<Slot> slots) {
        this.id = id;
        this.barcode = barcode;
        this.labwareType = labwareType;
        setSlots(slots);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getExternalBarcode() {
        return this.externalBarcode;
    }

    public void setExternalBarcode(String externalBarcode) {
        this.externalBarcode = externalBarcode;
    }

    public LabwareType getLabwareType() {
        return this.labwareType;
    }

    public void setLabwareType(LabwareType labwareType) {
        this.labwareType = labwareType;
    }

    public List<Slot> getSlots() {
        return this.slots;
    }

    public void setSlots(List<Slot> slots) {
        this.slots = newArrayList(slots);
    }

    public Slot getFirstSlot() {
        return this.slots.get(0);
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getCreated() {
        return this.created;
    }

    /**
     * Returns the slot with the given address from this labware.
     * Throws an exception if no such slot exists
     * @param address the address of the slot to get
     * @return the slot with the given address
     * @exception IllegalArgumentException no such slot exists
     * @exception IllegalStateException the slot at the index expected for that address does not have the expected address
     */
    public Slot getSlot(Address address) {
        int index = labwareType.indexOf(address);
        if (index < 0) {
            throw new IllegalArgumentException("Address "+address+" is not valid for labware type "+labwareType.getName());
        }
        Slot slot = getSlots().get(index);
        if (!slot.getAddress().equals(address)) {
            throw new IllegalStateException("Expected slot "+address+" at index "+index+" in labware "
                    +getBarcode()+" but found "+slot.getAddress());
        }
        return slot;
    }

    public boolean isDiscarded() {
        return this.discarded;
    }

    public void setDiscarded(boolean discarded) {
        this.discarded = discarded;
    }

    public boolean isReleased() {
        return this.released;
    }

    public void setReleased(boolean released) {
        this.released = released;
    }

    public boolean isDestroyed() {
        return this.destroyed;
    }

    public void setDestroyed(boolean destroyed) {
        this.destroyed = destroyed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Labware that = (Labware) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.externalBarcode, that.externalBarcode)
                && Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.slots, that.slots)
                && this.discarded == that.discarded
                && this.released == that.released
                && this.destroyed == that.destroyed);
    }

    @Override
    public String toString() {
        return String.format("Labware(%s)", repr(barcode));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : barcode!=null ? barcode.hashCode() : 0);
    }

    @JsonIgnore
    public boolean isUsable() {
        return !(isReleased() || isDestroyed() || isDiscarded());
    }

    @JsonIgnore
    public boolean isEmpty() {
        return this.slots.stream().allMatch(slot -> slot.getSamples().isEmpty());
    }

    @JsonIgnore
    public Labware.State getState() {
        if (isDestroyed()) return State.destroyed;
        if (isReleased()) return State.released;
        if (isDiscarded()) return State.discarded;
        if (isEmpty()) return State.empty;
        return State.active;
    }
}
