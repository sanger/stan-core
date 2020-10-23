package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

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
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("message", message)
                .add("user", user)
                .toString();
    }
}
