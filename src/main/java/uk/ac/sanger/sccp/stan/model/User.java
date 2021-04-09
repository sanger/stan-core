package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * A user of the application
 * @author dr6
 */
@Entity
public class User {
    /**
     * User roles, which correspond to sets of privileges
     */
    public enum Role {
        // Note that the order here is significant to how includes(role) works
        disabled, normal, admin;

        public boolean includes(Role other) {
            return this.ordinal() >= other.ordinal();
        }
    }

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    private String username;

    @Column(columnDefinition = "enum('disabled', 'normal', 'admin')")
    @Enumerated(EnumType.STRING)
    private Role role;

    public User() {}

    public User(Integer id, String username, Role role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public User(String username, Role role) {
        this(null, username, role);
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

    public Role getRole() {
        return this.role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean hasRole(Role role) {
        return (this.role!=null && this.role.includes(role));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("username", username)
                .add("role", role)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User that = (User) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.username, that.username)
                && this.role==that.role);
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : username!=null ? username.hashCode() : 0);
    }
}
