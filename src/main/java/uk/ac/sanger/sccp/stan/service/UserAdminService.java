package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;

import static java.util.Objects.requireNonNull;
import static uk.ac.sanger.sccp.utils.BasicUtils.trimAndRequire;

/**
 * Service for admin of {@link User Users}
 * @author dr6
 */
@Service
public class UserAdminService {
    private final UserRepo userRepo;
    private final Validator<String> usernameValidator;

    @Autowired
    public UserAdminService(UserRepo userRepo, @Qualifier("usernameValidator") Validator<String> usernameValidator) {
        this.userRepo = userRepo;
        this.usernameValidator = usernameValidator;
    }

    /**
     * Creates a new user with the given username.
     * The username will be stripped and made lower case.
     * @param username the username
     * @return the newly created user
     * @exception IllegalArgumentException if the username is unsuitable
     * @exception EntityExistsException if the user already exists
     */
    public User addUser(String username) {
        username = trimAndRequire(username, "Username not supplied.").toLowerCase();
        usernameValidator.checkArgument(username);
        if (userRepo.findByUsername(username).isPresent()) {
            throw new EntityExistsException("User already exists: "+username);
        }
        return userRepo.save(new User(null, username, User.Role.normal));
    }

    /**
     * Sets the role of a user.
     * If the user already has the given role, no update will be performed
     * You cannot set a role to or from admin.
     * @param username the name of an existing user
     * @param role the role to set the user to
     * @return the updated user
     * @exception IllegalArgumentException the username is missing or the operation is disallowed
     * @exception EntityNotFoundException the specified user does not exist
     */
    public User setUserRole(String username, User.Role role) {
        username = trimAndRequire(username, "Username not supplied.");
        requireNonNull(role, "Role not supplied.");
        User user = userRepo.getByUsername(username);
        if (user.getRole()==role) {
            return user;
        }
        if (role==User.Role.admin) {
            throw new IllegalArgumentException("Cannot promote a user to admin.");
        }
        if (user.getRole()==User.Role.admin) {
            throw new IllegalArgumentException("Cannot set the role of an admin user.");
        }
        user.setRole(role);
        return userRepo.save(user);
    }
}
