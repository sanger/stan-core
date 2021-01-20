package uk.ac.sanger.sccp.stan.model.store;

import com.google.common.base.MoreObjects;
import org.json.JSONException;
import org.json.JSONObject;
import uk.ac.sanger.sccp.stan.model.Address;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * An object representing a storage location, not including the contents or the child locations.
 * @author dr6
 */
public class LinkedLocation {
    private static final String NAME_FIELD = "name";
    private static final String CUSTOM_NAME_FIELD = "customName";
    private String barcode;
    private String description;
    private Address address;
    private Size size;

    public String getBarcode() {
        return this.barcode;
    }

    public String getDescription() {
        return this.description;
    }

    public Address getAddress() {
        return this.address;
    }

    public Size getSize() {
        return this.size;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public void setSize(Size size) {
        this.size = size;
    }

    public String getName() {
        if (description==null || description.isEmpty()) {
            return null;
        }
        try {
            JSONObject jo = new JSONObject(description);
            return jo.getString(NAME_FIELD);
        } catch (JSONException je) {
            return null;
        }
    }

    public String getCustomName() {
        if (description==null || description.isEmpty()) {
            return null;
        }
        try {
            JSONObject jo = new JSONObject(description);
            return jo.getString(CUSTOM_NAME_FIELD);
        } catch (JSONException je) {
            return null;
        }
    }

    public void setName(String name) {
        setNameAndCustomName(name, getCustomName());
    }

    public void setCustomName(String customName) {
        setNameAndCustomName(getName(), customName);
    }

    public void setNameAndCustomName(String name, String customName) {
        name = sanitise(name);
        customName = sanitise(customName);
        if (name==null && customName==null) {
            setDescription(null);
            return;
        }
        JSONObject jo = new JSONObject();
        jo.put(NAME_FIELD, name);
        jo.put(CUSTOM_NAME_FIELD, customName);
        setDescription(jo.toString());
    }

    private static String sanitise(String string) {
        if (string!=null) {
            string = string.trim();
            if (string.isEmpty()) {
                return null;
            }
        }
        return string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkedLocation that = (LinkedLocation) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.description, that.description)
                && Objects.equals(this.address, that.address)
                && Objects.equals(this.size, that.size));
    }

    @Override
    public int hashCode() {
        return (barcode!=null ? barcode.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("barcode", repr(barcode))
                .add("description", repr(description))
                .add("address", address)
                .add("size", size)
                .omitNullValues()
                .toString()
                ;
    }
}
