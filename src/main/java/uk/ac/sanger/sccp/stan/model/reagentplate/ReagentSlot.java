package uk.ac.sanger.sccp.stan.model.reagentplate;

import org.hibernate.annotations.Formula;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.Objects;

/**
 * A slot in a {@link ReagentPlate}
 * @author dr6
 */
@Entity
@Table(name="reagent_slot")
public class ReagentSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name="plate_id")
    private Integer plateId;
    @Embedded
    private Address address;
    @Formula("exists(select 1 from reagent_action ra where ra.reagent_slot_id=id limit 1)")
    private boolean used;

    public ReagentSlot() {}

    public ReagentSlot(Integer plateId, Address address) {
        this(null, plateId, address, false);
    }

    public ReagentSlot(Integer id, Integer plateId, Address address, boolean used) {
        this.id = id;
        this.plateId = plateId;
        this.address = address;
        this.used = used;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getPlateId() {
        return this.plateId;
    }

    public void setPlateId(Integer plateId) {
        this.plateId = plateId;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public boolean isUsed() {
        return this.used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReagentSlot that = (ReagentSlot) o;
        return (this.used == that.used
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.plateId, that.plateId)
                && Objects.equals(this.address, that.address));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : Objects.hash(plateId, address));
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ReagentSlot")
                .add("id", id)
                .add("plateId", plateId)
                .add("address", address)
                .add("used", used)
                .toString();
    }
}
