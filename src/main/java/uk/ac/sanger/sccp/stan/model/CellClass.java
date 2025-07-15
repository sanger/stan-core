package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * A cellular classification
 * @author dr6
 */
@Entity
public class CellClass implements HasName, HasIntId, HasEnabled {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private boolean hmdmcRequired = false;
    private boolean enabled = true;

    // required constructor
    public CellClass() {}

    public CellClass(Integer id, String name, boolean hmdmcRequired, boolean enabled) {
        this.id = id;
        this.name = name;
        this.hmdmcRequired = hmdmcRequired;
        this.enabled = enabled;
    }

    public CellClass(String name) {
        this(null, name, false, true);
    }

    @Override
    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isHmdmcRequired() {
        return this.hmdmcRequired;
    }

    public void setHmdmcRequired(boolean hmdmcRequired) {
        this.hmdmcRequired = hmdmcRequired;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CellClass that = (CellClass) o;
        return (this.hmdmcRequired == that.hmdmcRequired
                && this.enabled == that.enabled
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name));
    }

    @Override
    public int hashCode() {
        return (id != null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }

    @Override
    public String toString() {
        return getName();
    }
}
