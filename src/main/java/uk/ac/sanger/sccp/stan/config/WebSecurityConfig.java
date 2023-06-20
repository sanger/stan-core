package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * @author dr6
 */
@Configuration
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    private final LDAPConfig ldapConfig;

    @Autowired
    public WebSecurityConfig(LDAPConfig ldapConfig) {
        this.ldapConfig = ldapConfig;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/graphql").permitAll()
                .antMatchers("/graphiql").permitAll()
            .and()
                .csrf()
                .ignoringRequestMatchers(r -> "/files".equalsIgnoreCase(r.getRequestURI()))
                .ignoringRequestMatchers(r -> "/register/section".equalsIgnoreCase(r.getRequestURI()))
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
    }


    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.ldapAuthentication()
                .userDnPatterns(ldapConfig.getUserDnPatterns())
                .groupSearchBase(ldapConfig.getGroupSearchBase())
                .contextSource()
                .url(ldapConfig.getProviderUrl());
    }
}
