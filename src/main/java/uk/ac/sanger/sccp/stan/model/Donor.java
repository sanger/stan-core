package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class Donor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String donorName;
    @Column(columnDefinition = "enum('adult', 'paediatric', 'fetal')")
    @Enumerated(EnumType.STRING)
    private LifeStage lifeStage;

    public Donor() {}

    public Donor(Integer id, String donorName, LifeStage lifeStage) {
        this.id = id;
        this.donorName = donorName;
        this.lifeStage = lifeStage;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDonorName() {
        return this.donorName;
    }

    public void setDonorName(String donorName) {
        this.donorName = donorName;
    }

    public LifeStage getLifeStage() {
        return this.lifeStage;
    }

    public void setLifeStage(LifeStage lifeStage) {
        this.lifeStage = lifeStage;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("donorName", donorName)
                .add("lifeStage", lifeStage)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Donor that = (Donor) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.donorName, that.donorName)
                && this.lifeStage == that.lifeStage);
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : donorName!=null ? donorName.hashCode() : 0);
    }
}


