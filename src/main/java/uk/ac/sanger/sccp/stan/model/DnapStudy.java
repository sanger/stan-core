package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * A DNAP study
 * @author dr6
 */
@Entity
public class DnapStudy implements HasIntId, HasEnabled, HasName {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Integer id;
    private Integer ssId;
    private String name;
    private boolean enabled = true;

    public DnapStudy() {}

    public DnapStudy(Integer id, Integer ssId, String name, boolean enabled) {
        this.id = id;
        this.ssId = ssId;
        this.name = name;
        this.enabled = enabled;
    }

    public DnapStudy(Integer ssId, String name) {
        this(null, ssId, name, true);
    }

    @Override
    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getSsId() {
        return this.ssId;
    }

    public void setSsId(Integer ssId) {
        this.ssId = ssId;
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
    public String toString() {
        return getSsId()+": "+getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DnapStudy that = (DnapStudy) o;
        return (this.enabled == that.enabled
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.ssId, that.ssId)
                && Objects.equals(this.name, that.name));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : ssId!=null ? ssId.hashCode() : enabled ? 1 : 0);
    }
}
