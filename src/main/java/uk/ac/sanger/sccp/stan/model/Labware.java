package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.List;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class Labware {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String barcode;

    @ManyToOne
    private LabwareType labwareType;

    @OneToMany
    @OrderBy("address.row, address.column")
    private List<Slot> slots;

    public Labware() {}

    public Labware(Integer id, String barcode, LabwareType labwareType, List<Slot> slots) {
        this.id = id;
        this.barcode = barcode;
        this.labwareType = labwareType;
        this.slots = slots;
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
        this.slots = slots;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Labware that = (Labware) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.slots, that.slots));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : barcode!=null ? barcode.hashCode() : 0);
    }
}
