package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.List;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class TissueType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String code;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "tissueType")
    private List<SpatialLocation> spatialLocations;

    public TissueType() {}

    public TissueType(Integer id, String name, String code) {
        this.id = id;
        this.name = name;
        this.code = code;
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

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<SpatialLocation> getSpatialLocations() {
        return this.spatialLocations;
    }

    public void setSpatialLocations(List<SpatialLocation> spatialLocations) {
        this.spatialLocations = spatialLocations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TissueType that = (TissueType) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.code, that.code));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }
}
