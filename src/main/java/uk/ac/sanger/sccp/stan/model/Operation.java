package uk.ac.sanger.sccp.stan.model;

import org.hibernate.annotations.*;
import org.jetbrains.annotations.NotNull;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.Entity;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * An operation is a piece of work done on one or more labware.
 * Operations have a type, indicating what the operation was;
 * and actions, describing the interactions of slots and samples.
 * @author dr6
 */
@Entity
@DynamicInsert
public class Operation implements Comparable<Operation> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    private OperationType operationType;

    @Generated(GenerationTime.INSERT)
    private LocalDateTime performed;

    @OneToMany
    @JoinColumn(name="operation_id")
    private List<Action> actions;

    @ManyToOne
    private User user;

    private Integer planOperationId;

    @ManyToOne
    @JoinTable(name="operation_equipment",
            joinColumns = @JoinColumn(name="operation_id"),
            inverseJoinColumns = @JoinColumn(name="equipment_id"))
    private Equipment equipment;

    public Operation() {}

    public Operation(Integer id, OperationType operationType, LocalDateTime performed, List<Action> actions, User user) {
        this(id, operationType, performed, actions, user, null);
    }

    public Operation(Integer id, OperationType operationType, LocalDateTime performed, List<Action> actions, User user, Integer planOperationId) {
        this(id, operationType, performed, actions, user, planOperationId, null);
    }

    public Operation(Integer id, OperationType operationType, LocalDateTime performed, List<Action> actions, User user,
                     Integer planOperationId, Equipment equipment) {
        this.id = id;
        this.operationType = operationType;
        this.performed = performed;
        this.actions = actions;
        this.user = user;
        this.planOperationId = planOperationId;
        this.equipment = equipment;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDateTime getPerformed() {
        return this.performed;
    }

    public void setPerformed(LocalDateTime performed) {
        this.performed = performed;
    }

    public OperationType getOperationType() {
        return this.operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public List<Action> getActions() {
        return this.actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    /**
     * The id of the plan (if any) that was recorded in advance setting up this operation
     */
    public Integer getPlanOperationId() {
        return this.planOperationId;
    }

    public void setPlanOperationId(Integer planOperationId) {
        this.planOperationId = planOperationId;
    }

    /** The equipment, if any, used in this operation */
    public Equipment getEquipment() {
        return this.equipment;
    }

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Operation that = (Operation) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.performed, that.performed)
                && Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.actions, that.actions)
                && Objects.equals(this.planOperationId, that.planOperationId)
                && Objects.equals(this.equipment, that.equipment));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("id", id)
                .add("performed", performed)
                .add("operationType", operationType)
                .addIfNotNull("equipment", equipment)
                .toString();
    }

    /**
     * Compares operations by timestamp and then by id.
     */
    @Override
    public int compareTo(@NotNull Operation other) {
        if (this==other) {
            return 0;
        }
        int c = this.getPerformed().compareTo(other.getPerformed());
        if (c==0) {
            c = this.getId().compareTo(other.getId());
        }
        return c;
    }
}
