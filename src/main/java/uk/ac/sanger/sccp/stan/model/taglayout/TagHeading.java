package uk.ac.sanger.sccp.stan.model.taglayout;

import uk.ac.sanger.sccp.stan.model.Address;

import javax.persistence.*;
import java.util.Map;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * A heading and set of tag entries in a tag layout
 * @author dr6
 */
@Entity
@Table(name="tag_heading")
public class TagHeading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private boolean inRelease;

    @ElementCollection
    @CollectionTable(name="tag_entry")
    @Column(name="value")
    private Map<Address, String> entries = Map.of();

    public TagHeading() {}

    public TagHeading(String name) {
        setName(name);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /** The name of the tag heading */
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /* The entries for this tag heading in the tag layout */
    public Map<Address, String> getEntries() {
        return this.entries;
    }

    public void setEntries(Map<Address, String> entries) {
        this.entries = nullToEmpty(entries);
    }

    /** Should this heading be included in a release file? */
    public boolean isInRelease() {
        return this.inRelease;
    }

    public void setInRelease(boolean inRelease) {
        this.inRelease = inRelease;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagHeading that = (TagHeading) o;
        return (this.inRelease == that.inRelease
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.entries, that.entries));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }
}
