package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * A request to find labware
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

    public String getLabwareBarcode() {
        return this.labwareBarcode;
    }

    public void setLabwareBarcode(String labwareBarcode) {
        this.labwareBarcode = labwareBarcode;
    }

    public String getDonorName() {
        return this.donorName;
    }

    public void setDonorName(String donorName) {
        this.donorName = donorName;
    }

    public String getTissueExternalName() {
        return this.tissueExternalName;
    }

    public void setTissueExternalName(String tissueExternalName) {
        this.tissueExternalName = tissueExternalName;
    }

    public String getTissueTypeName() {
        return this.tissueTypeName;
    }

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
