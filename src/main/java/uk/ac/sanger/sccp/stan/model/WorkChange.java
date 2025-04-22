package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * A record of operations' work being changed.
 * @author dr6
 */
@Entity
public class WorkChange implements HasIntId {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer userId;

    public WorkChange() {}

    public WorkChange(Integer id, Integer userId) {
        this.id = id;
        this.userId = userId;
    }

    @Override
    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return this.userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkChange that = (WorkChange) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.userId, that.userId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId);
    }

    @Override
    public String toString() {
        return String.format("WorkChange(id=%s, userId=%s)", id, userId);
    }
}
