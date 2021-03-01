package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Entity
public class ReleaseDestination {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private boolean enabled = true;

    public ReleaseDestination() {}

    public ReleaseDestination(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

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

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseDestination that = (ReleaseDestination) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && this.enabled==that.enabled);
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }

    @Override
    public String toString() {
        return String.format("ReleaseDestination(%s, %s%s)", id, repr(name), enabled?"":" (disabled)");
    }
}
