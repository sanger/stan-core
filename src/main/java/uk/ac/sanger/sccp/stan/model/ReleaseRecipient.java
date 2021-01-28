package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Entity
public class ReleaseRecipient {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    private String username;
    private boolean enabled = true;

    public ReleaseRecipient() {}

    public ReleaseRecipient(Integer id, String username) {
        this.id = id;
        this.username = username;
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

    public boolean isEnabled() {
        return this.enabled;
    }

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
                && Objects.equals(this.username, that.username));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : username!=null ? username.hashCode() : 0);
    }


    @Override
    public String toString() {
        return String.format("ReleaseRecipient(%s, %s%s)", id, repr(username), enabled?"":" (disabled)");
    }
}
