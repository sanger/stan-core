package uk.ac.sanger.sccp.stan.request;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.Location;

import java.util.List;
import java.util.Objects;

/**
 * The result of a find request.
 * <ul>
 * <li>A list of {@link #getEntries entries} corresponding to the results of the find.
 * This contains ids of samples and labware.
 * <li>A list of the relevant samples.
 * <li>A list of the relevant labware.
 * <li>A list of the locations of the labware, indicated by labware id and location id (and maybe address).
 * <li>A list of the relevant locations.
 * </ul>
 * @author dr6
 */
public class FindResult {
    private int numRecords;
    private List<FindEntry> entries;
    private List<Sample> samples;
    private List<Labware> labware;
    private List<LabwareLocation> labwareLocations;
    private List<Location> locations;

    public FindResult() {}

    public FindResult(int numRecords, List<FindEntry> entries, List<Sample> samples, List<Labware> labware,
                      List<LabwareLocation> labwareLocations, List<Location> locations) {
        this.numRecords = numRecords;
        this.entries = entries;
        this.samples = samples;
        this.labware = labware;
        this.labwareLocations = labwareLocations;
        this.locations = locations;
    }

    /**
     * A list of sample ids and labware ids indicating the results of the search
     */
    public List<FindEntry> getEntries() {
        return this.entries;
    }

    /**
     * Sets the entries indicating the results of the search
     */
    public void setEntries(List<FindEntry> entries) {
        this.entries = entries;
    }

    /**
     * A list of the samples corresponding to the sample ids in the entries.
     */
    public List<Sample> getSamples() {
        return this.samples;
    }

    /**
     * Sets the list of the samples corresponding to the sample ids in the entries.
     */
    public void setSamples(List<Sample> samples) {
        this.samples = samples;
    }

    /**
     * A list of the labware corresponding to the labware ids in the entries.
     */
    public List<Labware> getLabware() {
        return this.labware;
    }

    /**
     * Sets the list of the labware corresponding to the labware ids in the entries.
     */
    public void setLabware(List<Labware> labware) {
        this.labware = labware;
    }

    /**
     * A list of the locations corresponding to the location ids in the LabwareLocations.
     */
    public List<Location> getLocations() {
        return this.locations;
    }

    /**
     * Sets the list of the locations corresponding to the location ids in the LabwareLocations.
     */
    public void setLocations(List<Location> locations) {
        this.locations = locations;
    }

    /**
     * The number of records found by this search, even if only a subset of those records are included.
     */
    public int getNumRecords() {
        return this.numRecords;
    }

    /**
     * Sets the number of records found by this search.
     */
    public void setNumRecords(int numRecords) {
        this.numRecords = numRecords;
    }

    /**
     * A list of labware ids, location ids and addresses indicating the locations of labware.
     */
    public List<LabwareLocation> getLabwareLocations() {
        return this.labwareLocations;
    }

    /**
     * Sets the list of LabwareLocations that indicates the locations of the labware.
     */
    public void setLabwareLocations(List<LabwareLocation> labwareLocations) {
        this.labwareLocations = labwareLocations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FindResult that = (FindResult) o;
        return (Objects.equals(this.entries, that.entries)
                && Objects.equals(this.labwareLocations, that.labwareLocations)
                && Objects.equals(this.samples, that.samples)
                && Objects.equals(this.labware, that.labware)
                && Objects.equals(this.locations, that.locations));
    }

    @Override
    public int hashCode() {
        return (entries!=null ? entries.hashCode() : 0);
    }

    @Override
    public String toString() {
        return String.format("FindResult(entries=%s, labwareLocations=%s)",
                 entries, labwareLocations);
    }

    /**
     * An individual entry from our results.
     * This contains a sample id and a labware id. The objects referred to by those ids
     * should be included in the appropriate fields of the result object.
     */
    public static class FindEntry {
        private int sampleId;
        private int labwareId;

        public FindEntry() {}

        public FindEntry(int sampleId, int labwareId) {
            this.sampleId = sampleId;
            this.labwareId = labwareId;
        }

        /** The id of a sample */
        public int getSampleId() {
            return this.sampleId;
        }

        /** Sets the sample id for this entry */
        public void setSampleId(int sampleId) {
            this.sampleId = sampleId;
        }

        /** The id of an item of labware */
        public int getLabwareId() {
            return this.labwareId;
        }

        /** Sets the labware id for this entry */
        public void setLabwareId(int labwareId) {
            this.labwareId = labwareId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FindEntry that = (FindEntry) o;
            return (this.sampleId == that.sampleId
                    && this.labwareId == that.labwareId);
        }

        @Override
        public int hashCode() {
            return 31*sampleId + labwareId;
        }

        @Override
        public String toString() {
            return String.format("(sampleId=%s, labwareId=%s)", sampleId, labwareId);
        }
    }

    /**
     * A relationship between a labware and a location, indicated by their ids.
     * The objects referred to by those ids should be included in the appropriate fields of the result object.
     */
    public static class LabwareLocation {
        private int labwareId;
        private int locationId;
        private Address address;

        public LabwareLocation() {}

        public LabwareLocation(int labwareId, int locationId) {
            this(labwareId, locationId, null);
        }

        public LabwareLocation(int labwareId, int locationId, Address address) {
            this.labwareId = labwareId;
            this.locationId = locationId;
            this.address = address;
        }

        /** The id of an item of labware */
        public int getLabwareId() {
            return this.labwareId;
        }

        /** Sets the labware id for this object */
        public void setLabwareId(int labwareId) {
            this.labwareId = labwareId;
        }

        /** The id of a location */
        public int getLocationId() {
            return this.locationId;
        }

        /** Sets the location id for this object */
        public void setLocationId(int locationId) {
            this.locationId = locationId;
        }

        /** The address of the labware in the location (may be null) */
        public Address getAddress() {
            return this.address;
        }

        /** Sets the address for this object (null permitted) */
        public void setAddress(Address address) {
            this.address = address;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LabwareLocation that = (LabwareLocation) o;
            return (this.labwareId == that.labwareId
                    && this.locationId == that.locationId
                    && Objects.equals(this.address, that.address));
        }

        @Override
        public int hashCode() {
            return Objects.hash(labwareId, locationId, address);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("labwareId", labwareId)
                    .add("locationId", locationId)
                    .add("address", address)
                    .omitNullValues()
                    .toString();
        }
    }
}
