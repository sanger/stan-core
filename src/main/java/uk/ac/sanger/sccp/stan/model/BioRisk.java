package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * Biological risk assessment number.
 * @author dr6
 */
@Entity
public class BioRisk implements HasIntId, HasEnabled {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String code;
    private boolean enabled = true;

    public BioRisk() {} // required no-arg constructor

    public BioRisk(Integer id, String code, boolean enabled) {
        this.id = id;
        this.code = code;
        this.enabled = enabled;
    }

    public BioRisk(Integer id, String code) {
        this(id, code, true);
    }

    public BioRisk(String code) {
        this(null, code, true);
    }

    /** Primary key */
    @Override
    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /** The alphanumeric code representing this risk assessment. */
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
    public String toString() {
        return describe(this)
                .add("id", id)
                .add("code", code)
                .add("enabled", enabled)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != this.getClass()) return false;
        BioRisk that = (BioRisk) o;
        return (this.enabled == that.enabled
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.code, that.code)
        );
    }

    @Override
    public int hashCode() {
        return (id != null ? id.hashCode() : code !=null ? code.hashCode() : 0);
    }
}