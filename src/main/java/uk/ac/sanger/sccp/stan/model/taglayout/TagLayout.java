package uk.ac.sanger.sccp.stan.model.taglayout;

import javax.persistence.*;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * @author dr6
 */
@Entity
public class TagLayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    public TagLayout(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public TagLayout() {}

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

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

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
}
