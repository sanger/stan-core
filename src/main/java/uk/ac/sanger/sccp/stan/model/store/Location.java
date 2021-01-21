package uk.ac.sanger.sccp.stan.model.store;

import java.util.*;

/**
 * An object representing a storage location, including info about the stored contents and the child locations.
 * @author dr6
 */
public class Location extends LinkedLocation {
    private LinkedLocation parent;
    private List<StoredItem> stored = new ArrayList<>();
    private List<LinkedLocation> children = new ArrayList<>();

    public List<StoredItem> getStored() {
        return this.stored;
    }

    public void setStored(Collection<StoredItem> stored) {
        this.stored = (stored==null ? new ArrayList<>() : new ArrayList<>(stored));
    }

    public List<LinkedLocation> getChildren() {
        return this.children;
    }

    public void setChildren(Collection<LinkedLocation> children) {
        this.children = (children==null ? new ArrayList<>() : new ArrayList<>(children));
    }

    public LinkedLocation getParent() {
        return this.parent;
    }

    public void setParent(LinkedLocation parent) {
        this.parent = parent;
    }

    public Location fixInternalLinks() {
        if (stored!=null && !stored.isEmpty()) {
            for (StoredItem si : stored) {
                si.setLocation(this);
            }
        }
        return this;
    }
}
