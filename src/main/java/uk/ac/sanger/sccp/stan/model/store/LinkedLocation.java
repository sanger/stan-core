package uk.ac.sanger.sccp.stan.model.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.Address;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * An object representing a storage location, not including the contents or the child locations.
 * @author dr6
 */
public class LinkedLocation {
    private static final String NAME_SEP = ": ";
    private String barcode;
    private String name;
    private Address address;
    private int numStored;
    private int numChildren;

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public String getFixedName() {
        if (name ==null || name.isEmpty()) {
            return null;
        }
        int n = name.indexOf(NAME_SEP);
        if (n < 0) {
            return name;
        }
        if (n==0) {
            return null;
        }
        return name.substring(0, n);
    }

    public String getCustomName() {
        if (name ==null || name.isEmpty()) {
            return null;
        }
        int n = name.indexOf(NAME_SEP);
        if (n < 0) {
            return null;
        }
        return name.substring(n + NAME_SEP.length());
    }

    public void setFixedName(String name) {
        setNameAndCustomName(name, getCustomName());
    }

    public void setCustomName(String customName) {
        setNameAndCustomName(getFixedName(), customName);
    }

    public void setNameAndCustomName(String fixedName, String customName) {
        fixedName = sanitise(fixedName);
        customName = sanitise(customName);
        if (customName==null) {
            this.name = fixedName;
        } else if (fixedName==null) {
            this.name = NAME_SEP + customName;
        } else {
            this.name = fixedName + NAME_SEP + customName;
        }
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

    public int getNumStored() {
        return this.numStored;
    }

    public void setNumStored(int numStored) {
        this.numStored = numStored;
    }

    public int getNumChildren() {
        return this.numChildren;
    }

    public void setNumChildren(int numChildren) {
        this.numChildren = numChildren;
    }

    @JsonIgnore
    public boolean isLeaf() {
        return getNumChildren() == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return equalsLinkedLocation((LinkedLocation) o);
    }

    protected boolean equalsLinkedLocation(LinkedLocation that) {
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.address, that.address)
                && this.numStored == that.numStored
                && this.numChildren == that.numChildren
        );
    }

    @Override
    public int hashCode() {
        return (barcode!=null ? barcode.hashCode() : 0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("barcode", repr(barcode))
                .add("name", repr(name))
                .add("address", address)
                .add("numStored", numStored)
                .add("numChildren", numChildren)
                .omitNullValues()
                .toString();
    }
}
