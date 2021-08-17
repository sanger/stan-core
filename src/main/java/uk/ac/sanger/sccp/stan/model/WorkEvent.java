package uk.ac.sanger.sccp.stan.model;

import org.hibernate.annotations.*;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.Entity;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * An event in the life cycle of a {@link Work}
 * @author dr6
 */
@Entity
@DynamicInsert
public class WorkEvent {
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
    private Work work;

    @ManyToOne
    private User user;

    @ManyToOne
    private Comment comment;

    @Generated(GenerationTime.INSERT)
    private LocalDateTime performed;

    public WorkEvent() {}

    public WorkEvent(Integer id, Work work, Type type, User user, Comment comment, LocalDateTime performed) {
        this.id = id;
        this.work = work;
        this.type = type;
        this.user = user;
        this.comment = comment;
        this.performed = performed;
    }

    public WorkEvent(Work work, Type type, User user, Comment comment) {
        this(null, work, type, user, comment, null);
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

    public Work getWork() {
        return this.work;
    }

    public void setWork(Work work) {
        this.work = work;
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
        WorkEvent that = (WorkEvent) o;
        return (Objects.equals(this.id, that.id)
                && this.type == that.type
                && Objects.equals(this.work, that.work)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.comment, that.comment)
                && Objects.equals(this.performed, that.performed));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : Objects.hash(type, work, user, comment, performed));
    }

    @Override
    public String toString() {
        return BasicUtils.describe("WorkEvent")
                .add("id", id)
                .add("type", type)
                .add("work", work)
                .add("user", user)
                .add("comment", comment)
                .add("performed", performed)
                .toString();
    }
}
