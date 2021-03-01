package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class OperationType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private int flags;

    public OperationType() {}

    public OperationType(Integer id, String name) {
        this(id, name, 0);
    }

    public OperationType(Integer id, String name, int flags) {
        this.id = id;
        this.name = name;
        this.flags = flags;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFlags() {
        return this.flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public boolean has(OperationTypeFlag flag) {
        return (this.flags & flag.bit()) != 0;
    }

    //region operation behaviour
    public boolean inPlace() {
        return this.has(OperationTypeFlag.IN_PLACE);
    }

    public boolean canPrelabel() {
        return !inPlace();
    }

    public boolean canCreateSection() {
        return !inPlace();
    }

    public boolean sourceMustBeBlock() {
        return this.has(OperationTypeFlag.SOURCE_IS_BLOCK);
    }
    //endregion

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationType that = (OperationType) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && this.flags==that.flags);
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
