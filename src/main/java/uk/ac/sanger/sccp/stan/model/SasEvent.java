package uk.ac.sanger.sccp.stan.model;

import org.hibernate.annotations.*;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * An event in the life cycle of an {@link SasNumber SAS number}
 * @author dr6
 */
@Entity
@Table(name="sas_event")
@DynamicInsert
public class SasEvent {
    public enum Type {
        create, pause, resume, complete, fail
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "enum('create', 'pause', 'resume', 'complete', 'fail')")
    @Enumerated(EnumType.STRING)
    private Type type;

    @ManyToOne
    @JoinColumn(name="sas_id")
    private SasNumber sasNumber;

    @ManyToOne
    private User user;

    @ManyToOne
    private Comment comment;

    @Generated(GenerationTime.INSERT)
    private LocalDateTime performed;

    public SasEvent() {}

    public SasEvent(Integer id, SasNumber sasNumber, Type type, User user, Comment comment, LocalDateTime performed) {
        this.id = id;
        this.sasNumber = sasNumber;
        this.type = type;
        this.user = user;
        this.comment = comment;
        this.performed = performed;
    }

    public SasEvent(SasNumber sasNumber, Type type, User user, Comment comment) {
        this(null, sasNumber, type, user, comment, null);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Type getType() {
        return this.type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public SasNumber getSasNumber() {
        return this.sasNumber;
    }

    public void setSasNumber(SasNumber sasNumber) {
        this.sasNumber = sasNumber;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Comment getComment() {
        return this.comment;
    }

    public void setComment(Comment comment) {
        this.comment = comment;
    }

    public LocalDateTime getPerformed() {
        return this.performed;
    }

    public void setPerformed(LocalDateTime performed) {
        this.performed = performed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SasEvent that = (SasEvent) o;
        return (Objects.equals(this.id, that.id)
                && this.type == that.type
                && Objects.equals(this.sasNumber, that.sasNumber)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.comment, that.comment)
                && Objects.equals(this.performed, that.performed));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : Objects.hash(type, sasNumber, user, comment, performed));
    }

    @Override
    public String toString() {
        return BasicUtils.describe("SasEvent")
                .add("id", id)
                .add("type", type)
                .add("sasNumber", sasNumber)
                .add("user", user)
                .add("comment", comment)
                .add("performed", performed)
                .toString();
    }
}
