package uk.ac.sanger.sccp.stan;

import graphql.schema.DataFetcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.model.LoginResult;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.service.LDAPService;

import java.util.ArrayList;

/**
 * @author dr6
 */
@Component
public class GraphQLDataFetchers {
    final LDAPService ldapService;
    final SessionConfig sessionConfig;

    @Autowired
    public GraphQLDataFetchers(LDAPService ldapService, SessionConfig sessionConfig) {
        this.ldapService = ldapService;
        this.sessionConfig = sessionConfig;
    }

    public DataFetcher<LoginResult> logIn() {
        return dataFetchingEnvironment -> {
            String username = dataFetchingEnvironment.getArgument("username");
            String password = dataFetchingEnvironment.getArgument("password");
            if (!ldapService.verifyCredentials(username, password)) {
                return new LoginResult("Login failed", null);
            }
            Authentication authentication = new UsernamePasswordAuthenticationToken(username, password, new ArrayList<>());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            attr.getRequest().getSession().setMaxInactiveInterval(60 * this.sessionConfig.getMaxInactiveMinutes());
            return new LoginResult("OK", new User(username.toLowerCase()));
        };
    }

    public DataFetcher<User> getUser() {
        return dataFetchingEnvironment -> {
            SecurityContext sc = SecurityContextHolder.getContext();
            Authentication auth = (sc==null ? null : sc.getAuthentication());
            if (auth==null || auth instanceof AnonymousAuthenticationToken || auth.getPrincipal()==null) {
                return null;
            }
            return new User(auth.getPrincipal().toString());
        };
    }

    public DataFetcher<String> logOut() {
        return dataFetchingEnvironment -> {
            SecurityContextHolder.getContext().setAuthentication(null);
            return "OK";
        };
    }
}
