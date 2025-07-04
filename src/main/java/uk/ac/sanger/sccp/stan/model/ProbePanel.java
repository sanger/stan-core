package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * A probe panel.
 * @author dr6
 */
@Entity
public class ProbePanel implements HasIntId, HasName, HasEnabled {
    public enum ProbeType { xenium, cytassist, spike }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "enum('xenium', 'cytassist', 'spike')")
    @Enumerated(EnumType.STRING)
    private ProbeType type;

    private String name;
    private boolean enabled;

    public ProbePanel() {}

    public ProbePanel(ProbeType type, String name) {
        this(null, type, name, true);
    }

    public ProbePanel(Integer id, ProbeType type, String name) {
        this(id, type, name, true);
    }

    public ProbePanel(Integer id, ProbeType type, String name, boolean enabled) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.enabled = enabled;
    }

    /**
     * Primary key
     */
    @Override
    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ProbeType getType() {
        return this.type;
    }

    public void setType(ProbeType type) {
        this.type = type;
    }

    /**
     * The name of the probe panel.
     */
    @Override
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Whether the probe panel is available for use.
     */
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
        ProbePanel that = (ProbePanel) o;
        return (this.enabled == that.enabled
                && this.type == that.type
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }

    @Override
    public String toString() {
        return getName();
    }
}