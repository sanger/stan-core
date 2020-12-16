package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class Printer {
    public enum Service { sprint }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    @ManyToOne
    private LabelType labelType;

    @Column(columnDefinition = "enum('sprint')")
    @Enumerated(EnumType.STRING)
    private Service service;

    public Printer() {}

    public Printer(Integer id, String name, LabelType labelType, Service service) {
        this.id = id;
        this.name = name;
        this.labelType = labelType;
        this.service = service;
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

    public LabelType getLabelType() {
        return this.labelType;
    }

    public void setLabelType(LabelType labelType) {
        this.labelType = labelType;
    }

    public Service getService() {
        return this.service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Printer that = (Printer) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.labelType, that.labelType)
                && this.service == that.service);
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
