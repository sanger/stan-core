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
    private Location location;
    private Address address;
    private Integer addressIndex;

    public StoredItem() {}

    public StoredItem(String barcode, Location location) {
        this(barcode, location, null);
    }

    public StoredItem(String barcode, Location location, Address address) {
        this.barcode = barcode;
        this.location = location;
        this.address = address;
    }

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

    public Integer getAddressIndex() {
        return this.addressIndex;
    }

    public void setAddressIndex(Integer addressIndex) {
        this.addressIndex = addressIndex;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("barcode", repr(barcode))
                .add("location", repr(getLocationBarcode()))
                .add("address", address)
                .add("addressIndex", addressIndex)
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
                && Objects.equals(this.getLocationBarcode(), that.getLocationBarcode())
                && Objects.equals(this.addressIndex, that.addressIndex)
        );
    }

    /**
     * Sets this location's content to this instance where it has the same barcode.
     * Calls {@link Location#fixInternalLinks} on the location.
     * Does nothing if the location is null.
     * @return this stored item
     */
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
