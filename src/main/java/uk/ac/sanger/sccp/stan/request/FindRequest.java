package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A request to find labware.
 * Each individual string field may be null, but in combination the non-null fields must be sufficient to
 * specify a valid search.
 * @author dr6
 */
public class FindRequest {
    private String labwareBarcode;
    private List<String> donorNames;
    private List<String> tissueExternalNames;
    private String tissueTypeName;
    private String workNumber;
    private String labwareTypeName;
    private LocalDate createdMin;
    private LocalDate createdMax;
    private String species;

    private int maxRecords = -1;

    // deserialisation constructor
    public FindRequest() {}

    public FindRequest(String labwareBarcode, List<String> donorNames, List<String> tissueExternalNames, String tissueTypeName,
                       int maxRecords, String workNumber, String labwareTypeName, LocalDate createdMin, LocalDate createdMax) {
        this.labwareBarcode = labwareBarcode;
        this.donorNames = donorNames;
        this.tissueExternalNames = tissueExternalNames;
        this.tissueTypeName = tissueTypeName;
        this.maxRecords = maxRecords;
        this.workNumber = workNumber;
        this.labwareTypeName = labwareTypeName;
        this.createdMin = createdMin;
        this.createdMax = createdMax;
    }

    /**
     * A specific labware barcode to find
     */
    public String getLabwareBarcode() {
        return this.labwareBarcode;
    }

    /**
     * Sets the specific labware barcode to find
     */
    public void setLabwareBarcode(String labwareBarcode) {
        this.labwareBarcode = labwareBarcode;
    }

    /**
     * The name of a donor to find
     */
    public List<String> getDonorNames() {
        return this.donorNames;
    }

    /**
     * Sets the name of a donor to find
     */
    public void setDonorNames(List<String> donorNames) {
        this.donorNames = donorNames;
    }

    /**
     * A tissue external name to find
     */
    public List<String> getTissueExternalNames() {
        return this.tissueExternalNames;
    }

    /**
     * Sets the tissue external name to find
     */
    public void setTissueExternalNames(List<String> tissueExternalNames) {
        this.tissueExternalNames = tissueExternalNames;
    }

    /**
     * A tissue type name to find
     */
    public String getTissueTypeName() {
        return this.tissueTypeName;
    }

    /**
     * Sets the tissue type name to find
     */
    public void setTissueTypeName(String tissueTypeName) {
        this.tissueTypeName = tissueTypeName;
    }

    /** The name of a labware type to find. */
    public String getLabwareTypeName() {
        return this.labwareTypeName;
    }

    /** Sets the name of the required labware type */
    public void setLabwareTypeName(String labwareTypeName) {
        this.labwareTypeName = labwareTypeName;
    }

    /**
     * The maximum number of records this search should return.
     * If {@code maxRecords < 0} then there is no explicit limit.
     * @return the maximum number of records this search should return
     */
    public int getMaxRecords() {
        return this.maxRecords;
    }

    /**
     * Sets the maximum number of records this search should return.
     * If {@code maxRecords < 0} then there is no explicit limit.
     * @param maxRecords the maximum number of records this search should return
     */
    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    /**
     * A work number to find
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    /**
     * Sets the work number to find
     */
    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }


    public LocalDate getCreatedMin() {
        return this.createdMin;
    }

    public void setCreatedMin(LocalDate createdMin) {
        this.createdMin = createdMin;
    }

    public LocalDate getCreatedMax() {
        return this.createdMax;
    }

    public void setCreatedMax(LocalDate createdMax) {
        this.createdMax = createdMax;
    }

    public String getSpecies() {
        return this.species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FindRequest that = (FindRequest) o;
        return (Objects.equals(this.labwareBarcode, that.labwareBarcode)
                && Objects.equals(this.donorNames, that.donorNames)
                && Objects.equals(this.tissueExternalNames, that.tissueExternalNames)
                && Objects.equals(this.tissueTypeName, that.tissueTypeName)
                && Objects.equals(this.labwareTypeName, that.labwareTypeName)
                && this.maxRecords==that.maxRecords
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.species, that.species)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(labwareBarcode, donorNames, tissueExternalNames, labwareTypeName, tissueTypeName, maxRecords);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("labwareBarcode", labwareBarcode)
                .add("donorName", donorNames)
                .add("tissueExternalName", tissueExternalNames)
                .add("tissueTypeName", tissueTypeName)
                .add("labwareTypeName", labwareTypeName)
                .add("maxRecords", maxRecords)
                .add("workNumber", workNumber)
                .add("createdMin", createdMin)
                .add("createdMax", createdMax)
                .add("species", species)
                .omitNullValues()
                .reprStringValues()
                .toString();
    }
}
