package uk.ac.sanger.sccp.stan.model.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.Address;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * An object representing a stored item
 * @author dr6
 */
public class StoredItem {
    private String barcode;
    private Address address;
    private Location location;

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Location getLocation() {
        return this.location;
    }

    @JsonIgnore
    public String getLocationBarcode() {
        return (this.location!=null ? this.location.getBarcode() : null);
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("barcode", repr(barcode))
                .add("address", address)
                .add("location", repr(getLocationBarcode()))
                .omitNullValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoredItem that = (StoredItem) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.address, that.address)
                && Objects.equals(this.getLocationBarcode(), that.getLocationBarcode()));
    }

    public StoredItem fixInternalLinks() {
        if (location!=null) {
            List<StoredItem> storedItems = location.getStored();
            final int num = storedItems.size();
            for (int i = 0; i < num; ++i) {
                if (storedItems.get(i).getBarcode().equals(this.barcode)) {
                    storedItems.set(i, this);
                }
            }
            location.fixInternalLinks();
        }
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, address, getLocationBarcode());
    }
}
