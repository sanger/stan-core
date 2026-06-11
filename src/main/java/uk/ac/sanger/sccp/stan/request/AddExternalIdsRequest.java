package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
public class AddExternalIdsRequest {
    private String labwareBarcode;
    private List<AddressExternalName> addressNames;

    public AddExternalIdsRequest() {}

    public AddExternalIdsRequest(String labwareBarcode, List<AddressExternalName> addressNames) {
        setLabwareBarcode(labwareBarcode);
        setAddressNames(addressNames);
    }

    public String getLabwareBarcode() {
        return labwareBarcode;
    }

    public void setLabwareBarcode(String labwareBarcode) {
        this.labwareBarcode = labwareBarcode;
    }

    public List<AddressExternalName> getAddressNames() {
        return this.addressNames;
    }

    public void setAddressNames(List<AddressExternalName> addressNames) {
        this.addressNames = addressNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddExternalIdsRequest that = (AddExternalIdsRequest) o;
        return Objects.equals(labwareBarcode, that.labwareBarcode) && Objects.equals(addressNames, that.addressNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labwareBarcode, addressNames);
    }

    @Override
    public String toString() {
        return String.format("AddExternalIDRequest(%s, %s)", repr(labwareBarcode), addressNames);
    }

    /** A slot address and the external name for the tissue in that slot */
    public static class AddressExternalName {
        private Address address;
        private String externalName;

        public AddressExternalName() {}

        public AddressExternalName(Address address, String externalName) {
            this.address = address;
            this.externalName = externalName;
        }

        public Address getAddress() {
            return this.address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        public String getExternalName() {
            return this.externalName;
        }

        public void setExternalName(String externalName) {
            this.externalName = externalName;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            AddressExternalName that = (AddressExternalName) o;
            return (Objects.equals(this.address, that.address)
                    && Objects.equals(this.externalName, that.externalName));
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, externalName);
        }

        @Override
        public String toString() {
            return String.format("%s: %s", address, repr(externalName));
        }
    }
}

