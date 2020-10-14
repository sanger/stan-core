package uk.ac.sanger.sccp.stan;

import graphql.schema.DataFetcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.model.LoginResult;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.service.LDAPService;

/**
 * @author dr6
 */
@Component
public class GraphQLDataFetchers {
    final LDAPService ldapService;

    @Autowired
    public GraphQLDataFetchers(LDAPService ldapService) {
        this.ldapService = ldapService;
    }

    public DataFetcher<LoginResult> login() {
        return dataFetchingEnvironment -> {
            String username = dataFetchingEnvironment.getArgument("username");
            String password = dataFetchingEnvironment.getArgument("password");
            if (ldapService.verifyCredentials(username, password)) {
                return new LoginResult("OK", new User(username.toLowerCase()));
            }
            return new LoginResult("Login failed", null);
        };
    }
}
