package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A record of an item of labware being released
 * @author dr6
 */
@Entity
@Table(name="labware_release")
public class Release {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    private Labware labware;

    @ManyToOne
    private User user;

    @ManyToOne
    private ReleaseDestination destination;

    @ManyToOne
    private ReleaseRecipient recipient;

    @Generated(GenerationTime.INSERT)
    private Timestamp released;

    private Integer snapshotId;

    public Release() {}

    public Release(Labware labware, User user, ReleaseDestination destination, ReleaseRecipient recipient, Integer snapshotId) {
        this(null, labware, user, destination, recipient, snapshotId, null);
    }

    public Release(Integer id, Labware labware, User user, ReleaseDestination destination, ReleaseRecipient recipient, Integer snapshotId, Timestamp released) {
        this.id = id;
        this.labware = labware;
        this.user = user;
        this.destination = destination;
        this.recipient = recipient;
        this.released = released;
        this.snapshotId = snapshotId;
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

    public ReleaseDestination getDestination() {
        return this.destination;
    }

    public void setDestination(ReleaseDestination destination) {
        this.destination = destination;
    }

    public ReleaseRecipient getRecipient() {
        return this.recipient;
    }

    public void setRecipient(ReleaseRecipient recipient) {
        this.recipient = recipient;
    }

    public Timestamp getReleased() {
        return this.released;
    }

    public void setReleased(Timestamp released) {
        this.released = released;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Integer getSnapshotId() {
        return this.snapshotId;
    }

    public void setSnapshotId(Integer snapshotId) {
        this.snapshotId = snapshotId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Release that = (Release) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.labware, that.labware)
                && Objects.equals(this.destination, that.destination)
                && Objects.equals(this.recipient, that.recipient)
                && Objects.equals(this.released, that.released)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.snapshotId, that.snapshotId));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : labware!=null ? labware.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("labware", labware==null ? null : repr(labware.getBarcode()))
                .add("user", user==null ? null : repr(user.getUsername()))
                .add("destination", destination)
                .add("recipient", recipient)
                .add("released", released)
                .add("snapshotId", snapshotId)
                .toString();
    }
}
