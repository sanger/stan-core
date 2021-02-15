package uk.ac.sanger.sccp.stan.request;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.Location;

import java.util.List;
import java.util.Objects;

/**
 * The result of a find request.
 * A list of {@link #getEntries entries} corresponding to the results of the find.
 * This contains ids of samples and labware.
 * A list of the relevant samples.
 * A list of the relevant labware.
 * A list of the locations of the labware, indicated by labware id and location id (and maybe address).
 * A list of the relevant locations.
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

    public List<FindEntry> getEntries() {
        return this.entries;
    }

    public void setEntries(List<FindEntry> entries) {
        this.entries = entries;
    }

    public List<Sample> getSamples() {
        return this.samples;
    }

    public void setSamples(List<Sample> samples) {
        this.samples = samples;
    }

    public List<Labware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<Labware> labware) {
        this.labware = labware;
    }

    public List<Location> getLocations() {
        return this.locations;
    }

    public void setLocations(List<Location> locations) {
        this.locations = locations;
    }

    public int getNumRecords() {
        return this.numRecords;
    }

    public void setNumRecords(int numRecords) {
        this.numRecords = numRecords;
    }

    public List<LabwareLocation> getLabwareLocations() {
        return this.labwareLocations;
    }

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

    public static class FindEntry {
        private int sampleId;
        private int labwareId;

        public FindEntry() {}

        public FindEntry(int sampleId, int labwareId) {
            this.sampleId = sampleId;
            this.labwareId = labwareId;
        }

        public int getSampleId() {
            return this.sampleId;
        }

        public void setSampleId(int sampleId) {
            this.sampleId = sampleId;
        }

        public int getLabwareId() {
            return this.labwareId;
        }

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

        public int getLabwareId() {
            return this.labwareId;
        }

        public void setLabwareId(int labwareId) {
            this.labwareId = labwareId;
        }

        public int getLocationId() {
            return this.locationId;
        }

        public void setLocationId(int locationId) {
            this.locationId = locationId;
        }

        public Address getAddress() {
            return this.address;
        }

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
