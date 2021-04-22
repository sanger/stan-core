package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class Hmdmc implements HasEnabled {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String hmdmc;
    private boolean enabled;

    public Hmdmc() {}

    public Hmdmc(Integer id, String hmdmc) {
        this.id = id;
        this.hmdmc = hmdmc;
        this.enabled = true;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getHmdmc() {
        return this.hmdmc;
    }

    public void setHmdmc(String hmdmc) {
        this.hmdmc = hmdmc;
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
        Hmdmc that = (Hmdmc) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.hmdmc, that.hmdmc)
                && this.enabled == that.enabled);
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : hmdmc!=null ? hmdmc.hashCode() : 0);
    }

    @Override
    public String toString() {
        return getHmdmc();
    }
}
