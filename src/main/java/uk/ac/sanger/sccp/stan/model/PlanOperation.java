package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * A recorded plan for an operation (created by prelabelling)
 * @author dr6
 */
@Entity
@DynamicInsert
public class PlanOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    private OperationType operationType;

    @Generated(GenerationTime.INSERT)
    private LocalDateTime planned;

    @OneToMany
    @JoinColumn(name="plan_operation_id")
    private List<PlanAction> planActions;

    @ManyToOne
    private User user;

    public PlanOperation() {}

    public PlanOperation(Integer id, OperationType opType, LocalDateTime planned, List<PlanAction> planActions, User user) {
        this.id = id;
        this.operationType = opType;
        this.planned = planned;
        this.planActions = planActions;
        this.user = user;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public OperationType getOperationType() {
        return this.operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public List<PlanAction> getPlanActions() {
        return this.planActions;
    }

    public void setPlanActions(List<PlanAction> planActions) {
        this.planActions = planActions;
    }

    public LocalDateTime getPlanned() {
        return this.planned;
    }

    public void setPlanned(LocalDateTime planned) {
        this.planned = planned;
    }


    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanOperation that = (PlanOperation) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.planned, that.planned)
                && Objects.equals(this.planActions, that.planActions)
                && Objects.equals(this.user, that.user));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("operationType", operationType)
                .add("planned", planned)
                .add("planActions", planActions)
                .add("user", user)
                .toString();
    }
}
