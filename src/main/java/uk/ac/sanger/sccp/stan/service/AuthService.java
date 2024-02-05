package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.LoginResult;

/**
 * Service dealing with user authentication
 */
public interface AuthService {
        /**
     * Logs in
     * @param username the username to log in
     * @param password the password to authenticate the user
     * @return a login result describing the result of the login
     */
    LoginResult logIn(String username, String password);

    /**
     * Logs out the current logged in user, if any
     * @return a description of the result of logging out
     */
    String logOut();

    /**
     * Creates a Stan user authenticated with the given credentials
     * @param username the username to create
     * @param password the password to authenticate the user
     * @param role the role to create the user with
     * @return the result of attempting to log in with the given credentials
     */
    LoginResult selfRegister(String username, String password, User.Role role);
}
