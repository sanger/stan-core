package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * A record of printing a label for a particular piece of labware
 * @author dr6
 */
@Entity
public class LabwarePrint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne
    private Printer printer;
    @ManyToOne
    private Labware labware;
    @ManyToOne
    private User user;
    @Generated(GenerationTime.INSERT)
    private Timestamp printed;

    public LabwarePrint() {}

    public LabwarePrint(Integer id, Printer printer, Labware labware, User user, Timestamp printed) {
        this.id = id;
        this.printer = printer;
        this.labware = labware;
        this.user = user;
        this.printed = printed;
    }

    public LabwarePrint(Printer printer, Labware labware, User user) {
        this(null, printer, labware, user, null);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Printer getPrinter() {
        return this.printer;
    }

    public void setPrinter(Printer printer) {
        this.printer = printer;
    }

    public Labware getLabware() {
        return this.labware;
    }

    public void setLabware(Labware labware) {
        this.labware = labware;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Timestamp getPrinted() {
        return this.printed;
    }

    public void setPrinted(Timestamp printed) {
        this.printed = printed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabwarePrint that = (LabwarePrint) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.printer, that.printer)
                && Objects.equals(this.labware, that.labware)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.printed, that.printed));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, printer, labware, user, printed);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("printer", printer)
                .add("labware", labware)
                .add("user", user)
                .add("printed", printed)
                .toString();
    }
}
