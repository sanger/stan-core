package uk.ac.sanger.sccp.stan.request;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.stan.model.User;

import java.util.Objects;

/**
 * @author dr6
 */
public class LoginResult {
    private String message;
    private User user;

    public LoginResult() {}

    public LoginResult(String message, User user) {
        this.message = message;
        this.user = user;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoginResult that = (LoginResult) o;
        return (Objects.equals(this.message, that.message)
                && Objects.equals(this.user, that.user));
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, user);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("message", message)
                .add("user", user)
                .toString();
    }
}
