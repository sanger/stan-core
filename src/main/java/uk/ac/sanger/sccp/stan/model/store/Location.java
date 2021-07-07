package uk.ac.sanger.sccp.stan.model.store;

import uk.ac.sanger.sccp.stan.model.Address;

import java.util.*;
import java.util.function.Function;

/**
 * An object representing a storage location, including info about the stored contents and the child locations.
 * @author dr6
 */
public class Location extends LinkedLocation {
    private Integer id;
    private LinkedLocation parent;
    private List<StoredItem> stored = new ArrayList<>();
    private List<LinkedLocation> children = new ArrayList<>();
    private Size size;
    private GridDirection direction;
    private String qualifiedNameWithFirstBarcode;

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

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

    public Size getSize() {
        return this.size;
    }

    public void setSize(Size size) {
        this.size = size;
    }

    public GridDirection getDirection() {
        return this.direction;
    }

    public void setDirection(GridDirection direction) {
        this.direction = direction;
    }

    public String getQualifiedNameWithFirstBarcode() {
        return this.qualifiedNameWithFirstBarcode;
    }

    public void setQualifiedNameWithFirstBarcode(String qualifiedNameWithFirstBarcode) {
        this.qualifiedNameWithFirstBarcode = qualifiedNameWithFirstBarcode;
    }

    public Location fixInternalLinks() {
        if (stored!=null && !stored.isEmpty()) {
            for (StoredItem si : stored) {
                si.setLocation(this);
            }
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Location that = (Location) o;
        if (!equalsLinkedLocation(that)) {
            return false;
        }
        return (Objects.equals(this.id, that.id) &&
                Objects.equals(this.size, that.size) &&
                Objects.equals(this.parentBarcode(), that.parentBarcode()) &&
                Objects.equals(this.qualifiedNameWithFirstBarcode, that.qualifiedNameWithFirstBarcode) &&
                this.direction==that.direction &&
                alike(this.stored, that.stored, StoredItem::getBarcode, StoredItem::getAddress) &&
                alike(this.children, that.children, LinkedLocation::getBarcode, LinkedLocation::getAddress));
    }

    protected String parentBarcode() {
        return (this.parent==null ? null : this.parent.getBarcode());
    }

    protected static <E> boolean alike(Collection<E> alpha, Collection<E> beta,
                                       Function<? super E, String> stringFunction,
                                       Function<? super E, Address> addressFunction) {
        if (alpha == null || alpha.isEmpty()) {
            return (beta == null || beta.isEmpty());
        }
        if (beta == null || beta.size() != alpha.size()) {
            return false;
        }
        Map<String, Address> alphaMap = new HashMap<>(alpha.size());
        Map<String, Address> betaMap = new HashMap<>(beta.size());
        for (E element : alpha) {
            alphaMap.put(stringFunction.apply(element), addressFunction.apply(element));
        }
        for (E element : beta) {
            betaMap.put(stringFunction.apply(element), addressFunction.apply(element));
        }
        return alphaMap.equals(betaMap);
    }

    @Override
    public int hashCode() {
        return id!=null ? id.hashCode() : Objects.hash(super.hashCode(), parent, stored, children, size, direction);
    }
}
