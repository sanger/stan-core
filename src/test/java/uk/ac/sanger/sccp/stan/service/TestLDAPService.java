package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.config.LDAPConfig;

import javax.naming.Context;
import javax.naming.NamingException;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link LDAPService}
 * @author dr6
 */
public class TestLDAPService {
    private LDAPConfig config;
    private LDAPService ldapService;

    @BeforeEach
    void setup() {
        config = new LDAPConfig("com.sun.jndi.ldap.LdapCtxFactory", "ldap-url",
                "simple", "42", "uid={0}", "ou=group");
        ldapService = spy(new LDAPService(config));
    }

    private void verifyLdap(String username, String password) throws NamingException {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, config.getContextFactory());
        environment.put(Context.PROVIDER_URL, config.getProviderUrl());
        environment.put(Context.SECURITY_AUTHENTICATION, config.getSecurityAuthentication());
        environment.put(Context.SECURITY_PRINCIPAL, config.getUserDnPatterns().replace("{0}", username));
        environment.put(Context.SECURITY_CREDENTIALS, password);

        verify(ldapService).accessLdap(environment);
    }

    @Test
    public void testSuccessfulLogin() throws NamingException {
        doNothing().when(ldapService).accessLdap(any());
        assertTrue(ldapService.verifyCredentials("jeff", "swordfish"));
        verifyLdap("jeff", "swordfish");
    }

    @Test
    public void testFailedLogin() throws NamingException {
        doThrow(new NamingException("Unit testing LdapService")).when(ldapService).accessLdap(any());
        assertFalse(ldapService.verifyCredentials("jeff", "swordfish"));
        verifyLdap("jeff", "swordfish");
    }
}
