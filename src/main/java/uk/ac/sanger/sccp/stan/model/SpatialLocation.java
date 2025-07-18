package uk.ac.sanger.sccp.stan.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.Objects;

/**
 * A location inside a particular tissue type, e.g. the location inside an organ that a particular
 * sample might come from.
 * @author dr6
 */
@Entity
public class SpatialLocation implements HasName, HasIntId, HasEnabled {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private Integer code;
    private boolean enabled = true;
    @ManyToOne
    private TissueType tissueType;

    public SpatialLocation() {}

    public SpatialLocation(Integer id, String name, Integer code, TissueType tissueType) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.tissueType = tissueType;
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

    /** The integer code that identifies a spatial location for a particular tissue type */
    public Integer getCode() {
        return this.code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public TissueType getTissueType() {
        return this.tissueType;
    }

    public void setTissueType(TissueType tissueType) {
        this.tissueType = tissueType;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @JsonIgnore
    public boolean isUsable() {
        return (this.isEnabled() && tissueType!=null && tissueType.isEnabled());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpatialLocation that = (SpatialLocation) o;
        if (this.getTissueType() != that.getTissueType()) {
            if (this.getTissueType()==null || that.getTissueType()==null) {
                return false;
            }
            if (!Objects.equals(this.getTissueType().getId(), that.getTissueType().getId())) {
                return false;
            }
        }
        return (this.enabled==that.enabled
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.code, that.code)
        );
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }

    @Override
    public String toString() {
        return String.format("%s: %s", getCode(), getName());
    }
}
