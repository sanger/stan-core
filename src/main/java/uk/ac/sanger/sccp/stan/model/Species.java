package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class Species implements HasEnabled {
    public static final String HUMAN_NAME = "Homo sapiens (Human)";
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private boolean enabled;

    public Species() {}

    public Species(Integer id, String name) {
        this.id = id;
        this.name = name;
        this.enabled = true;
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Species that = (Species) o;
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
        return getName();
    }

    public static boolean isHumanName(String name) {
        return name != null && (name.equalsIgnoreCase(HUMAN_NAME) || name.equalsIgnoreCase("Human"));
    }

    public boolean requiresHmdmc() {
        return isHumanName(getName());
    }
}
