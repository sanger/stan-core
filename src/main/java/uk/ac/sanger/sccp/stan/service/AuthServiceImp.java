package uk.ac.sanger.sccp.stan.service;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.AuthenticationComponent;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;
import uk.ac.sanger.sccp.stan.request.LoginResult;

import java.util.ArrayList;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class AuthServiceImp implements AuthService {
    Logger log = LoggerFactory.getLogger(AuthServiceImp.class);
    private final SessionConfig sessionConfig;
    private final UserRepo userRepo;
    private final AuthenticationComponent authComp;
    private final LDAPService ldapService;
    private final AdminNotifyService adminNotifyService;
    private final UserAdminService userAdminService;
    private final Transactor transactor;

    @Autowired
    public AuthServiceImp(SessionConfig sessionConfig,
                          UserRepo userRepo, AuthenticationComponent authComp,
                          LDAPService ldapService, AdminNotifyService adminNotifyService,
                          UserAdminService userAdminService,
                          Transactor transactor) {
        this.sessionConfig = sessionConfig;
        this.userRepo = userRepo;
        this.authComp = authComp;
        this.ldapService = ldapService;
        this.adminNotifyService = adminNotifyService;
        this.userAdminService = userAdminService;
        this.transactor = transactor;
    }

    /**
     * Gets the username of the logged in user, if any
     * @return the logged in username, or null
     */
    @Nullable
    public String loggedInUsername() {
        var auth = authComp.getAuthentication();
        if (auth != null) {
            var princ = auth.getPrincipal();
            if (princ instanceof User) {
                return ((User) princ).getUsername();
            }
        }
        return null;
    }

    @Override
    public LoginResult logIn(String username, String password) {
        if (log.isInfoEnabled()) {
            log.info("Login attempt by {}", repr(username));
        }
        Optional<User> optUser = userRepo.findByUsername(username);
        if (optUser.isEmpty()) {
            return new LoginResult("Username not in database.", null);
        }
        User user = optUser.get();
        if (user.getRole()==User.Role.disabled) {
            return new LoginResult("Username is disabled.", null);
        }
        if (!ldapService.verifyCredentials(username, password)) {
            return new LoginResult("Login failed.", null);
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, password, new ArrayList<>());
        authComp.setAuthentication(authentication, sessionConfig.getMaxInactiveMinutes());
        log.info("Login succeeded for user {}", user);
        return new LoginResult("OK", user);
    }

    @Override
    public String logOut() {
        if (log.isInfoEnabled()) {
            log.info("Logout requested by {}", repr(loggedInUsername()));
        }
        authComp.setAuthentication(null, 0);
        return "OK";
    }

    public LoginResult selfRegisterInTransaction(String username, String password, User.Role role) {
        if (log.isInfoEnabled()) {
            log.info("selfRegister attempt by {}", repr(username));
        }
        username = userAdminService.validateUsername(username);
        if (!ldapService.verifyCredentials(username, password)) {
            return new LoginResult("Authentication failed.", null);
        }
        Optional<User> optUser = userRepo.findByUsername(username);
        User user;
        boolean created;
        if (optUser.isEmpty()) {
            user = userAdminService.addUser(username, role);
            log.info("Login succeeded as new user {}", user);
            created = true;
        } else {
            user = optUser.get();
            if (user.getRole()== User.Role.disabled) {
                return new LoginResult("Username is disabled.", null);
            }
            log.info("Login succeeded for existing user {}", user);
            created = false;
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(user, password, new ArrayList<>());
        authComp.setAuthentication(authentication, sessionConfig.getMaxInactiveMinutes());
        return new LoginResult("OK", user, created);
    }

    @Override
    public LoginResult selfRegister(String username, String password, User.Role role) {
        LoginResult result = transactor.transact("selfRegister",
                () -> selfRegisterInTransaction(username, password, role));
        if (result.isCreated()) {
            sendNewUserEmail(result.getUser());
        }
        return result;
    }

    /**
     * Tries to send an email to admin users about the new user being created.
     * @param user the new user
     */
    public void sendNewUserEmail(User user) {
        String body = "User "+user.getUsername()+" has registered themself as "+user.getRole()+" on %service.";
        adminNotifyService.issue("user", "New user created on %service", body);
    }
}
