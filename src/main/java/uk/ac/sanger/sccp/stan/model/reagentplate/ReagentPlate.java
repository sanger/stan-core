package uk.ac.sanger.sccp.stan.model.reagentplate;

import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.*;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A plate of reagents where each slot may be transferred once to a labware
 * @author dr6
 */
@Entity
public class ReagentPlate implements HasIntId {
    public static final ReagentPlateType PLATE_TYPE_96 = new ReagentPlateType("Dual index plate",8,12);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String barcode;

    @OneToMany
    @JoinColumn(name="plate_id")
    @OrderBy("address.row, address.column")
    private List<ReagentSlot> slots;

    public ReagentPlate() {
        this(null, null, null);
    }

    public ReagentPlate(String barcode) {
        this(null, barcode, null);
    }

    public ReagentPlate(Integer id, String barcode, List<ReagentSlot> slots) {
        this.id = id;
        this.barcode = barcode;
        setSlots(slots);
    }

    @Override
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

    public List<ReagentSlot> getSlots() {
        return this.slots;
    }

    public void setSlots(List<ReagentSlot> slots) {
        this.slots = (slots==null ? List.of() : slots);
    }

    public ReagentPlateType getPlateType() {
        return PLATE_TYPE_96;
    }

    public Optional<ReagentSlot> optSlot(Address address) {
        int index = getPlateType().indexOf(address);
        if (index < 0) {
            return Optional.empty();
        }
        ReagentSlot slot = getSlots().get(index);
        if (!slot.getAddress().equals(address)) {
            throw new IllegalStateException("Expected slot "+address+" at index "+index+" in reagent plate "
                    +getBarcode()+" but found "+slot.getAddress());
        }
        return Optional.of(slot);
    }

    public ReagentSlot getSlot(Address address) {
        return optSlot(address)
                .orElseThrow(() -> new IllegalStateException("Address "+address+" is not valid in "+getPlateType().getName()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReagentPlate that = (ReagentPlate) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.slots, that.slots));
    }

    @Override
    public int hashCode() {
        return (id != null ? id.hashCode() : barcode!=null ? barcode.hashCode() : slots!=null ? slots.hashCode() : 1);
    }

    @Override
    public String toString() {
        return String.format("ReagentPlate(%s)", repr(barcode));
    }
}
