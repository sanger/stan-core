package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * A record that a slot is part of a particular group in a plan.
 * @author dr6
 */
@Entity
public class SlotGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer groupIndex;
    @ManyToOne
    private Slot slot;
    private Integer planId;
    private Integer operationId;

    public SlotGroup() {} // required by JPA

    public SlotGroup(Integer groupIndex, Slot slot, Integer planId) {
        this.groupIndex = groupIndex;
        this.slot = slot;
        this.planId = planId;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getGroupIndex() {
        return this.groupIndex;
    }

    public void setGroupIndex(Integer groupIndex) {
        this.groupIndex = groupIndex;
    }

    public Slot getSlot() {
        return this.slot;
    }

    public Integer getSlotId() {
        return (slot==null ? null : slot.getId());
    }

    public void setSlot(Slot slot) {
        this.slot = slot;
    }

    public Integer getPlanId() {
        return this.planId;
    }

    public void setPlanId(Integer planId) {
        this.planId = planId;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SlotGroup that = (SlotGroup) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.groupIndex, that.groupIndex)
                && Objects.equals(this.slot, that.slot)
                && Objects.equals(this.planId, that.planId)
                && Objects.equals(this.operationId, that.operationId));
    }

    @Override
    public int hashCode() {
        return (id != null ? id.hashCode() : Objects.hash(groupIndex, slot, planId, operationId));
    }

    @Override
    public String toString() {
        return describe(this)
                .add("id", id)
                .add("groupIndex", groupIndex)
                .add("slotId", getSlotId())
                .add("planId", planId)
                .add("operationId", operationId)
                .toString();
    }
}
