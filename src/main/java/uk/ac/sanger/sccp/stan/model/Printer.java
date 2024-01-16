package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.List;
import java.util.Objects;

/**
 * A database record of an available label printer
 * @author dr6
 */
@Entity
public class Printer {
    public enum Service { sprint }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    @ManyToMany
    @JoinTable(name = "printer_label_type", inverseJoinColumns = @JoinColumn(name="label_type_id"))
    @OrderBy("id")
    private List<LabelType> labelTypes;

    @Column(columnDefinition = "enum('sprint')")
    @Enumerated(EnumType.STRING)
    private Service service;

    public Printer() {}

    public Printer(Integer id, String name, List<LabelType> labelTypes, Service service) {
        this.id = id;
        this.name = name;
        this.labelTypes = labelTypes;
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

    public List<LabelType> getLabelTypes() {
        return this.labelTypes;
    }

    public void setLabelTypes(List<LabelType> labelTypes) {
        this.labelTypes = labelTypes;
    }

    /** What service is used to send requests to this printer? */
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
                && Objects.equals(this.labelTypes, that.labelTypes)
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
