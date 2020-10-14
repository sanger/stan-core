package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

/**
 * @author dr6
 */
public class User {
    private String username;

    public User() {}

    public User(String username) {
        this.username = username;
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
}
