package uk.ac.sanger.sccp.stan.model.taglayout;

import uk.ac.sanger.sccp.stan.model.Address;

import javax.persistence.*;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * A layout of tags in a plate
 * @author dr6
 */
@Entity
public class TagLayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    @OneToMany
    @JoinColumn(name="tag_layout_id")
    @OrderColumn(name="heading_index")
    private List<TagHeading> headings = List.of();

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /** The name of the tag layout */
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** The tag headings in this layout */
    public List<TagHeading> getHeadings() {
        return this.headings;
    }

    public void setHeadings(List<TagHeading> headings) {
        this.headings = nullToEmpty(headings);
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj==this) return true;
        if (obj==null || obj.getClass()!=this.getClass()) return false;
        TagLayout that = (TagLayout) obj;
        return (Objects.equals(this.getId(), that.getId())
                && Objects.equals(this.getName(), that.getName())
                && this.getHeadings().equals(that.getHeadings()));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }

    /**
     * Gets a map of tag heading to tag value for the given address.
     * Only headings are included if their {@link TagHeading#isInRelease isInRelease} method returns true
     * @param address the address to get tag data for
     * @return a map of tag heading to tag value
     */
    public Map<String, String> getTagData(Address address) {
        Map<String, String> map = new LinkedHashMap<>(headings.size());
        for (TagHeading heading : headings) {
            if (heading.isInRelease()) {
                map.put(heading.getName(), heading.getEntries().get(address));
            }
        }
        return map;
    }
}
