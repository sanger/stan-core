package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;
import org.hibernate.annotations.DynamicInsert;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
@DynamicInsert
public class Operation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    private OperationType operationType;

    private Timestamp performed;

    @OneToMany
    private List<Action> actions;

    @ManyToOne
    private User user;

    public Operation() {}

    public Operation(Integer id, OperationType operationType, Timestamp performed, List<Action> actions, User user) {
        this.id = id;
        this.operationType = operationType;
        this.performed = performed;
        this.actions = actions;
        this.user = user;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Timestamp getPerformed() {
        return this.performed;
    }

    public void setPerformed(Timestamp performed) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Operation that = (Operation) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.performed, that.performed)
                && Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.actions, that.actions));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("performed", performed)
                .add("operationType", operationType)
                .toString();
    }
}
