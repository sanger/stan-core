package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * A cost code that may be linked to SAS numbers.
 * @author dr6
 */
@Entity
public class CostCode implements HasEnabled {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String code;
    private boolean enabled;

    public CostCode() {}

    public CostCode(Integer id, String code, boolean enabled) {
        this.id = id;
        this.code = code;
        this.enabled = enabled;
    }

    public CostCode(Integer id, String code) {
        this(id, code, true);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CostCode that = (CostCode) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.code, that.code)
                && this.enabled==that.enabled);
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : code!=null ? code.hashCode() : 0);
    }

    @Override
    public String toString() {
        return getCode();
    }
}
