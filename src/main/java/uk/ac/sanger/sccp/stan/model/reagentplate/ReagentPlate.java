package uk.ac.sanger.sccp.stan.model.reagentplate;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.HasIntId;

import javax.persistence.*;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A plate of reagents where each slot may be transferred once to a labware
 * @author dr6
 */
@Entity
public class ReagentPlate implements HasIntId {
    public static final String TYPE_FRESH_FROZEN = "Fresh frozen - Dual Index TT Set A", TYPE_FFPE = "FFPE - Dual Index TS Set A";

    public static final ReagentPlateLayout PLATE_LAYOUT_96 = new ReagentPlateLayout("Dual index plate",8,12);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String barcode;
    private String plateType;
    private Integer tagLayoutId;

    @OneToMany
    @JoinColumn(name="plate_id")
    @OrderBy("address.row, address.column")
    private List<ReagentSlot> slots;

    public ReagentPlate() {
        this(null, null, null, null, null);
    }

    public ReagentPlate(String barcode, String plateType) {
        this(null, barcode, plateType, null, null);
    }

    public ReagentPlate(String barcode, String plateType, Integer tagLayoutId) {
        this(null, barcode, plateType, null, tagLayoutId);
    }

    public ReagentPlate(Integer id, String barcode, String plateType, List<ReagentSlot> slots, Integer tagLayoutId) {
        this.id = id;
        this.barcode = barcode;
        this.plateType = plateType;
        this.tagLayoutId = tagLayoutId;
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

    public String getPlateType() {
        return this.plateType;
    }

    public void setPlateType(String plateType) {
        this.plateType = plateType;
    }

    public List<ReagentSlot> getSlots() {
        return this.slots;
    }

    public void setSlots(List<ReagentSlot> slots) {
        this.slots = (slots==null ? List.of() : slots);
    }

    public ReagentPlateLayout getPlateLayout() {
        return PLATE_LAYOUT_96;
    }

    /**
     * Gets an optional of the slot at the given address in the reagent plate, if such a slot exists
     * @param address a slot address
     * @return an optional of the relevant reagent slot, if such a thing exists
     */
    public Optional<ReagentSlot> optSlot(Address address) {
        int index = getPlateLayout().indexOf(address);
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

    /**
     * Gets the slot at a given address; throws an exception if it does not exist
     * @param address a slot address
     * @return the reagent slot with the given address in this plate
     * @exception IllegalStateException the address is not valid in this plate
     */
    public ReagentSlot getSlot(Address address) {
        return optSlot(address)
                .orElseThrow(() -> new IllegalStateException("Address "+address+" is not valid in "+ getPlateLayout().getName()));
    }

    public Integer getTagLayoutId() {
        return this.tagLayoutId;
    }

    public void setTagLayoutId(Integer tagLayoutId) {
        this.tagLayoutId = tagLayoutId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReagentPlate that = (ReagentPlate) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.plateType, that.plateType)
                && Objects.equals(this.slots, that.slots)
                && Objects.equals(this.tagLayoutId, that.tagLayoutId));
    }

    @Override
    public int hashCode() {
        return (id != null ? id.hashCode() : barcode!=null ? barcode.hashCode() : slots!=null ? slots.hashCode() : 1);
    }

    @Override
    public String toString() {
        return String.format("ReagentPlate(%s)", repr(barcode));
    }

    /**
     * Returns the plate type in its canonical string form (one of the {@code TYPE_} constants in this class).
     * @param string a string that should be the name of a plate type
     * @return the matching plate type string in its canonical form; or null if none matches
     */
    public static String canonicalPlateType(String string) {
        if (string!=null) {
            if (string.equalsIgnoreCase(TYPE_FFPE)) {
                return TYPE_FFPE;
            }
            if (string.equalsIgnoreCase(TYPE_FRESH_FROZEN)) {
                return TYPE_FRESH_FROZEN;
            }
        }
        return null;
    }
}
