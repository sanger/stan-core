package uk.ac.sanger.sccp.stan.request;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * A request to add a tissue type.
 * @author dr6
 */
public class AddTissueTypeRequest {
    private String name;
    private String code;
    private List<NewSpatialLocation> spatialLocations = List.of();

    public AddTissueTypeRequest(String name, String code, List<NewSpatialLocation> spatialLocations) {
        setName(name);
        setCode(code);
        setSpatialLocations(spatialLocations);
    }

    public AddTissueTypeRequest(String name, String code) {
        this(name, code, null);
    }

    public AddTissueTypeRequest() {
        this(null, null, null);
    }

    /** The name of the tissue type. */
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** The short code for the tissue type. */
    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    /** The spatial locations for the new tissue type. */
    public List<NewSpatialLocation> getSpatialLocations() {
        return this.spatialLocations;
    }

    public void setSpatialLocations(List<NewSpatialLocation> spatialLocations) {
        this.spatialLocations = nullToEmpty(spatialLocations);
    }

    @Override
    public String toString() {
        return describe(this)
                .add("name", name)
                .add("code", code)
                .add("spatialLocations", spatialLocations)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != this.getClass()) return false;
        AddTissueTypeRequest that = (AddTissueTypeRequest) o;
        return (Objects.equals(this.name, that.name)
                && Objects.equals(this.code, that.code)
                && Objects.equals(this.spatialLocations, that.spatialLocations)
        );
    }

    @Override
    public int hashCode() {
        return (name==null ? 0 : name.hashCode());
    }

    /** A spatial location for the new tissue type */
    public static class NewSpatialLocation {
        private int code;
        private String name;

        public NewSpatialLocation(int code, String name) {
            this.code = code;
            this.name = name;
        }

        public NewSpatialLocation() {
            this(0, null);
        }

        /** The int code for the spatial location. */
        public int getCode() {
            return this.code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        /** The name of the spatial location. */
        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format("[%s: %s]", code, repr(name));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != this.getClass()) return false;
            NewSpatialLocation that = (NewSpatialLocation) o;
            return (this.code==that.code
                    && Objects.equals(this.name, that.name)
            );
        }

        @Override
        public int hashCode() {
            return (name==null ? 0 : name.hashCode());
        }
    }
}