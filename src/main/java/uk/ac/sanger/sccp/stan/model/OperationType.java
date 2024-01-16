package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * A type of operation. This indicates what the operation was that was being performed
 * @author dr6
 */
@Entity
public class OperationType implements HasName, HasIntId {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private int flags;
    @ManyToOne
    private BioState newBioState;

    public OperationType() {}

    public OperationType(Integer id, String name) {
        this(id, name, 0, null);
    }

    public OperationType(Integer id, String name, int flags, BioState newBioState) {
        this.id = id;
        this.name = name;
        this.flags = flags;
        this.newBioState = newBioState;
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

    /** A bitfield for flags that indicate the behaviour of operations of this type. */
    public int getFlags() {
        return this.flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    /** What new bio state (if any) should destination samples be in after this operation? */
    public BioState getNewBioState() {
        return this.newBioState;
    }

    public void setNewBioState(BioState newBioState) {
        this.newBioState = newBioState;
    }

    //region operation behaviour
    /** Checks if this operation type has a particular flag set */
    public boolean has(OperationTypeFlag flag) {
        return (this.flags & flag.bit()) != 0;
    }

    /** Is this operation in-place (i.e. is the source of each action expected to be the destination of that action) */
    public boolean inPlace() {
        return this.has(OperationTypeFlag.IN_PLACE);
    }

    /** Is it possible to prelabel labware for this operation? */
    public boolean canPrelabel() {
        return !inPlace();
    }

    /** Might this operation create sections? An operation in-place cannot create sections */
    public boolean canCreateSection() {
        return !inPlace();
    }

    /** Must this operation be recorded using a block as the source labware? */
    public boolean sourceMustBeBlock() {
        return this.has(OperationTypeFlag.SOURCE_IS_BLOCK);
    }

    /** Should the source labware be discarded after it is used in this operation? */
    public boolean discardSource() {
        return this.has(OperationTypeFlag.DISCARD_SOURCE);
    }

    /** Should the source labware be marked as used after it is used in this operation? */
    public boolean markSourceUsed() {
        return this.has(OperationTypeFlag.MARK_SOURCE_USED);
    }

    /** Does this operation transfer reagents from a reagent plate into the target labware? */
    public boolean transfersReagent() {
        return this.has(OperationTypeFlag.REAGENT_TRANSFER);
    }

    /** Is this operation a result? */
    public boolean isResult() {
        return this.has(OperationTypeFlag.RESULT);
    }

    /** Does this operation use labware probes? */
    public boolean usesProbes() {
        return this.has(OperationTypeFlag.PROBES);
    }

    /** Can a labware that is already active (and contains samples) be used as the destination? */
    public boolean supportsActiveDest() {
        return this.has(OperationTypeFlag.ACTIVE_DEST);
    }
    //endregion

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationType that = (OperationType) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && this.flags==that.flags
                && Objects.equals(this.newBioState, that.newBioState));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }

    @Override
    public String toString() {
        return getName();
    }
}
