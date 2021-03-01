package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * A request to find labware.
 * Each individual string field may be null, but in combination the non-null fields must be sufficient to
 * specify a valid search.
 * @author dr6
 */
public class FindRequest {
    private String labwareBarcode;
    private String donorName;
    private String tissueExternalName;
    private String tissueTypeName;

    private int maxRecords = -1;

    public FindRequest() {}

    public FindRequest(String labwareBarcode, String donorName, String tissueExternalName, String tissueTypeName,
                       int maxRecords) {
        this.labwareBarcode = labwareBarcode;
        this.donorName = donorName;
        this.tissueExternalName = tissueExternalName;
        this.tissueTypeName = tissueTypeName;
        this.maxRecords = maxRecords;
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
    public String getDonorName() {
        return this.donorName;
    }

    /**
     * Sets the name of a donor to find
     */
    public void setDonorName(String donorName) {
        this.donorName = donorName;
    }

    /**
     * A tissue external name to find
     */
    public String getTissueExternalName() {
        return this.tissueExternalName;
    }

    /**
     * Sets the tissue external name to find
     */
    public void setTissueExternalName(String tissueExternalName) {
        this.tissueExternalName = tissueExternalName;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FindRequest that = (FindRequest) o;
        return (Objects.equals(this.labwareBarcode, that.labwareBarcode)
                && Objects.equals(this.donorName, that.donorName)
                && Objects.equals(this.tissueExternalName, that.tissueExternalName)
                && Objects.equals(this.tissueTypeName, that.tissueTypeName)
                && this.maxRecords==that.maxRecords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labwareBarcode, donorName, tissueExternalName, tissueTypeName, maxRecords);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("labwareBarcode", labwareBarcode)
                .add("donorName", donorName)
                .add("tissueExternalName", tissueExternalName)
                .add("tissueTypeName", tissueTypeName)
                .add("maxRecords", maxRecords)
                .omitNullValues()
                .reprStringValues()
                .toString();
    }
}
