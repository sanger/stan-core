package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * @author dr6
 */
@Configuration
@PropertySource("classpath:ldap.properties")
public class LDAPConfig {
    @Value("${contextFactory}")
    String contextFactory;
    @Value("${providerUrl}")
    String providerUrl;
    @Value("${securityAuthentication}")
    String securityAuthentication;
    @Value("${securityPrincipalFormat}")
    String securityPrincipalFormat;
    @Value("${bypassPassword}")
    String bypassPassword;
}
