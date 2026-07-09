package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * These are user-managed entities that can be linked to a work
 * @author dr6
 */
@Entity
public class TreatmentType implements HasIntId, HasName, HasEnabled {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private boolean enabled = true;

    // For deserialisation
    public TreatmentType() {}

    public TreatmentType(Integer id, String name, boolean enabled) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
    }

    public TreatmentType(String name) {
        this(null, name, true);
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
        TreatmentType that = (TreatmentType) o;
        return (this.enabled == that.enabled
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name));
    }

    @Override
    public int hashCode() {
        return (id != null ? id.hashCode() : name != null ? name.hashCode() : 0);
    }
}
