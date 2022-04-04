package uk.ac.sanger.sccp.stan.model.reagentplate;

import uk.ac.sanger.sccp.stan.model.Slot;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.Objects;

/**
 * An action transferring a reagent as part of an operation.
 * @author dr6
 */
@Entity
@Table(name="reagent_action")
public class ReagentAction {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    private Integer operationId;

    @ManyToOne
    @JoinColumn(name="reagent_slot_id")
    private ReagentSlot reagentSlot;

    @ManyToOne
    @JoinColumn(name="destination_id")
    private Slot destination;

    public ReagentAction() {}

    public ReagentAction(Integer id, Integer operationId, ReagentSlot reagentSlot, Slot destination) {
        this.id = id;
        this.operationId = operationId;
        this.reagentSlot = reagentSlot;
        this.destination = destination;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
    }

    public ReagentSlot getReagentSlot() {
        return this.reagentSlot;
    }

    public void setReagentSlot(ReagentSlot reagentSlot) {
        this.reagentSlot = reagentSlot;
    }

    public Slot getDestination() {
        return this.destination;
    }

    public void setDestination(Slot destination) {
        this.destination = destination;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReagentAction that = (ReagentAction) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.reagentSlot, that.reagentSlot)
                && Objects.equals(this.destination, that.destination));
    }

    @Override
    public int hashCode() {
        return id!=null ? id.hashCode() : Objects.hash(operationId, reagentSlot, destination);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ReagentAction")
                .add("id", id)
                .add("operationId", operationId)
                .add("reagentSlot", reagentSlot)
                .add("destination", destination)
                .toString();
    }
}
