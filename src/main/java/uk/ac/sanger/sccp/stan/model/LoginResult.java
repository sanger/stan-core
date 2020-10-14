package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

/**
 * @author dr6
 */
public class LoginResult {
    private String message;
    private String cookie;

    public LoginResult() {}

    public LoginResult(String message, String cookie) {
        this.message = message;
        this.cookie = cookie;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCookie() {
        return this.cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("message", message)
                .add("cookie", cookie)
                .toString();
    }
}
