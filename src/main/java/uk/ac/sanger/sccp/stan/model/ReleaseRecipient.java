package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * An individual to whom labware may be released
 * @author dr6
 */
@Entity
public class ReleaseRecipient implements HasEnabled {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    private String username;
    private String fullName;
    private boolean enabled = true;

    public ReleaseRecipient() {}

    public ReleaseRecipient(Integer id, String username) {
        this(id, username, null);
    }

    public ReleaseRecipient(Integer id, String username, String fullName) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
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

    public String getFullName() {
        return this.fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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
        ReleaseRecipient that = (ReleaseRecipient) o;
        return (this.enabled == that.enabled
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.username, that.username)
                && Objects.equals(this.fullName, that.fullName));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : username!=null ? username.hashCode() : 0);
    }

    @Override
    public String toString() {
        return String.format("ReleaseRecipient(%s, %s, %s%s)", id, repr(username), repr(fullName), enabled?"":" (disabled)");
    }
}
