package uk.ac.sanger.sccp.stan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.config.LDAPConfig;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

/**
 * Based on the analogous class in CGAP LIMS.
 * @author dr6
 */
@Service
public class LDAPService {
    Logger log = LoggerFactory.getLogger(LDAPService.class);

    private final LDAPConfig ldapConfig;

    @Autowired
    public LDAPService(LDAPConfig ldapConfig) {
        this.ldapConfig = ldapConfig;
    }

    /**
     * Verifies your credentials in LDAP.
     * @return true if your credentials are verified; false if they are not
     */
    public boolean verifyCredentials(String username, String password) {
        String bypassPassword = ldapConfig.getBypassPassword();
        if (bypassPassword!=null && !bypassPassword.isEmpty() && bypassPassword.equals(password)) {
            log.info("Bypass password for username: "+username);
            return true;
        }
        Hashtable<String, String> environment = new Hashtable<>(5);
        environment.put(Context.INITIAL_CONTEXT_FACTORY, ldapConfig.getContextFactory());
        environment.put(Context.PROVIDER_URL, ldapConfig.getProviderUrl());
        environment.put(Context.SECURITY_AUTHENTICATION, ldapConfig.getSecurityAuthentication());
        environment.put(Context.SECURITY_PRINCIPAL, ldapConfig.getUserDnPatterns().replace("{0}", username));
        environment.put(Context.SECURITY_CREDENTIALS, password);
        try {
            accessLdap(environment);
        } catch (NamingException e) {
            log.error("LDAP check failed for "+username, e);
            return false;
        }
        return true;
    }

    void accessLdap(Hashtable<String, String> environment) throws NamingException {
        DirContext context = new InitialDirContext(environment);
        context.close();
    }
}
