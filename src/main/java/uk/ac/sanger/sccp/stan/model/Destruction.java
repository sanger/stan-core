package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * The destruction of an item of labware
 * @author dr6
 */
@Entity
public class Destruction {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    private Labware labware;

    @ManyToOne
    private User user;

    @Generated(GenerationTime.INSERT)
    private Timestamp destroyed;

    @ManyToOne
    private DestructionReason reason;

    public Destruction() {}

    public Destruction(Integer id, Labware labware, User user, Timestamp destroyed, DestructionReason reason) {
        this.id = id;
        this.labware = labware;
        this.user = user;
        this.destroyed = destroyed;
        this.reason = reason;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Labware getLabware() {
        return this.labware;
    }

    public void setLabware(Labware labware) {
        this.labware = labware;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Timestamp getDestroyed() {
        return this.destroyed;
    }

    public void setDestroyed(Timestamp destroyed) {
        this.destroyed = destroyed;
    }

    public DestructionReason getReason() {
        return this.reason;
    }

    public void setReason(DestructionReason reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Destruction that = (Destruction) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.labware, that.labware)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.destroyed, that.destroyed)
                && Objects.equals(this.reason, that.reason));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : labware!=null ? labware.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("labware", labware)
                .add("user", user)
                .add("destroyed", destroyed)
                .add("reason", reason)
                .toString();
    }
}
