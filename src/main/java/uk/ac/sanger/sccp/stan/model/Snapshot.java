package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * A snapshot of the contents of an item of labware
 * @author dr6
 */
@Entity
public class Snapshot {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    private Integer labwareId;

    @OneToMany
    @JoinColumn(name="snapshot_id")
    private List<SnapshotElement> elements;

    public Snapshot() {
        this(null, null, null);
    }

    public Snapshot(Integer labwareId) {
        this.labwareId = labwareId;
    }

    public Snapshot(Integer id, Integer labwareId, List<SnapshotElement> elements) {
        this.id = id;
        this.labwareId = labwareId;
        setElements(elements);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getLabwareId() {
        return this.labwareId;
    }

    public void setLabwareId(Integer labwareId) {
        this.labwareId = labwareId;
    }

    public List<SnapshotElement> getElements() {
        return this.elements;
    }

    public void setElements(Iterable<SnapshotElement> elements) {
        this.elements = newArrayList(elements);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Snapshot that = (Snapshot) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.labwareId, that.labwareId)
                && Objects.equals(this.elements, that.elements));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : labwareId!=null ? labwareId.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("labwareId", labwareId)
                .add("elements", elements)
                .toString();
    }
}
