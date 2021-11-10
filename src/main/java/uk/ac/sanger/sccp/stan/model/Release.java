package uk.ac.sanger.sccp.stan.model;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.time.LocalDateTime;
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
    private LocalDateTime released;

    private Integer snapshotId;

    private String locationBarcode;

    @AttributeOverrides({
            @AttributeOverride(name = "row", column = @Column(name = "storage_row")),
            @AttributeOverride(name = "column", column = @Column(name = "storage_col"))
    })
    @Embedded
    private Address storageAddress;

    public Release() {}

    public Release(Labware labware, User user, ReleaseDestination destination, ReleaseRecipient recipient, Integer snapshotId) {
        this(null, labware, user, destination, recipient, snapshotId, null, null, null);
    }
    public Release(Integer id, Labware labware, User user, ReleaseDestination destination, ReleaseRecipient recipient, Integer snapshotId, LocalDateTime released) {
        this(id, labware, user, destination, recipient, snapshotId, released, null, null);
    }
    public Release(Integer id, Labware labware, User user, ReleaseDestination destination, ReleaseRecipient recipient,
                   Integer snapshotId, LocalDateTime released, String locationBarcode, Address storageAddress) {
        this.id = id;
        this.labware = labware;
        this.user = user;
        this.destination = destination;
        this.recipient = recipient;
        this.released = released;
        this.snapshotId = snapshotId;
        this.locationBarcode = locationBarcode;
        this.storageAddress = storageAddress;
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

    public LocalDateTime getReleased() {
        return this.released;
    }

    public void setReleased(LocalDateTime released) {
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

    public String getLocationBarcode() {
        return this.locationBarcode;
    }

    public void setLocationBarcode(String locationBarcode) {
        this.locationBarcode = locationBarcode;
    }

    public Address getStorageAddress() {
        return this.storageAddress;
    }

    public void setStorageAddress(Address storageAddress) {
        this.storageAddress = storageAddress;
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
                && Objects.equals(this.snapshotId, that.snapshotId)
                && Objects.equals(this.locationBarcode, that.locationBarcode)
                && Objects.equals(this.storageAddress, that.storageAddress)
        );
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : labware!=null ? labware.hashCode() : 0);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("id", id)
                .add("labware", labware==null ? null : repr(labware.getBarcode()))
                .add("user", user==null ? null : repr(user.getUsername()))
                .add("destination", destination)
                .add("recipient", recipient)
                .add("released", released)
                .add("snapshotId", snapshotId)
                .addReprIfNotNull("locationBarcode", locationBarcode)
                .addIfNotNull("storageAddress", storageAddress)
                .toString();
    }
}
