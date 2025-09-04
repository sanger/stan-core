package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.containsIgnoreCase;

/**
 * A type of labware. This indicates the layout of the labware, its name, what kind of labels it uses,
 * and might indicate other functional changes for certain operations.
 * @author dr6
 */
@Entity
public class LabwareType implements HasIntId, HasName, HasEnabled {
    public static final String FETAL_WASTE_NAME = "Fetal waste container",
            PROVIASETTE_NAME = "Proviasette",
            CASSETTE_NAME = "Cassette",
            XENIUM_NAME = "Xenium";
    public static final List<String> CYTASSIST_BLOCK_MIDDLE_NAMES = List.of(
            "CytAssist 6.5", "CytAssist HD 6.5", "CytAssist HD 3' 6.5"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private int numRows = 1;
    private int numColumns = 1;
    private boolean prebarcoded;
    private boolean enabled = true;

    @ManyToOne
    private LabelType labelType;

    public LabwareType() {}

    public LabwareType(Integer id, String name, int numRows, int numColumns, LabelType labelType, boolean prebarcoded) {
        this.id = id;
        this.name = name;
        this.numRows = numRows;
        this.numColumns = numColumns;
        this.labelType = labelType;
        this.prebarcoded = prebarcoded;
    }

    @Override
    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNumRows() {
        return this.numRows;
    }

    public void setNumRows(int numRows) {
        this.numRows = numRows;
    }

    public int getNumColumns() {
        return this.numColumns;
    }

    public void setNumColumns(int numColumns) {
        this.numColumns = numColumns;
    }

    public LabelType getLabelType() {
        return this.labelType;
    }

    public void setLabelType(LabelType labelType) {
        this.labelType = labelType;
    }

    public boolean isPrebarcoded() {
        return this.prebarcoded;
    }

    public void setPrebarcoded(boolean prebarcoded) {
        this.prebarcoded = prebarcoded;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFetalWaste() {
        return FETAL_WASTE_NAME.equalsIgnoreCase(this.name);
    }

    /**
     * Should labware of this type show fixative in the state field on the label?
     */
    public boolean showFixativeAsStateOnLabel() {
        return (name!=null && (name.equalsIgnoreCase(PROVIASETTE_NAME) || name.equalsIgnoreCase(CASSETTE_NAME)));
    }

    public boolean isXenium() {
        return (name!=null && name.equalsIgnoreCase(XENIUM_NAME));
    }

    public boolean isCytAssist() {
        return (name != null && containsIgnoreCase(name, "CytAssist"));
    }

    /** Should slots B1 and C1 be blocked in cytassist op? */
    public boolean blockMiddleSlots() {
        return (name != null && CYTASSIST_BLOCK_MIDDLE_NAMES.stream().anyMatch(name::equalsIgnoreCase));
    }

    /**
     * Should samples be listed in column-major order on the label?
     */
    public boolean columnMajorOrderOnLabel() {
        return isXenium();
    }

    /**
     * Returns the index of the specified address in this labware type's valid addresses.
     * Returns -1 if the address is not valid for this labware type
     * @param address the address to find
     * @return the index found, or -1 if the address is not valid for this labware type
     */
    public int indexOf(Address address) {
        if (address.getRow() < 1 || address.getColumn() < 1
                || address.getRow() > numRows || address.getColumn() > numColumns) {
            return -1;
        }
        return (address.getRow()-1) * numColumns + address.getColumn()-1;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabwareType that = (LabwareType) o;
        return (this.numRows == that.numRows
                && this.numColumns == that.numColumns
                && this.prebarcoded == that.prebarcoded
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.labelType, that.labelType));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }
}
