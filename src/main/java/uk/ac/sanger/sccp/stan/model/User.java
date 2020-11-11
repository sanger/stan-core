package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class User {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    private String username;

    public User() {}

    public User(Integer id, String username) {
        this.id = id;
        this.username = username;
    }

    public User(String username) {
        this(null, username);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("username", username)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User that = (User) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.username, that.username));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : username!=null ? username.hashCode() : 0);
    }
}
