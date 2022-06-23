package uk.ac.sanger.sccp.stan.request;

import java.util.Objects;

public class AddExternalIDRequest {
    private String labwareBarcode;
    private String externalName;

    public AddExternalIDRequest() {}

    public AddExternalIDRequest(String labwareBarcode, String externalName) {
        setLabwareBarcode(labwareBarcode);
        setExternalName(externalName);
    }

    public String getLabwareBarcode() {
        return labwareBarcode;
    }

    public void setLabwareBarcode(String labwareBarcode) {
        this.labwareBarcode = labwareBarcode;
    }

    public String getExternalName() {
        return externalName;
    }

    public void setExternalName(String externalName) {
        this.externalName = externalName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddExternalIDRequest that = (AddExternalIDRequest) o;
        return Objects.equals(labwareBarcode, that.labwareBarcode) && Objects.equals(externalName, that.externalName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labwareBarcode, externalName);
    }

    @Override
    public String toString() {
        return "AddExternalIDRequest{" +
                "labwareBarcode='" + labwareBarcode + '\'' +
                ", externalName='" + externalName + '\'' +
                '}';
    }
}
