package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

/**
 * Base class for GraphQL data fetcher resources
 * @author dr6
 */
abstract class BaseGraphQLResource {
    final ObjectMapper objectMapper;
    final AuthenticationComponent authComp;
    final UserRepo userRepo;

    protected BaseGraphQLResource(ObjectMapper objectMapper, AuthenticationComponent authComp, UserRepo userRepo) {
        this.objectMapper = objectMapper;
        this.authComp = authComp;
        this.userRepo = userRepo;
    }

    protected User checkUser(User.Role role) {
        Authentication auth = authComp.getAuthentication();
        if (auth != null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof User) {
                User user = (User) principal;
                if (!user.hasRole(role)) {
                    throw new InsufficientAuthenticationException("Requires role: "+role);
                }
                return user;
            }
        }
        throw new AuthenticationCredentialsNotFoundException("Not logged in");
    }

    protected <E> E arg(DataFetchingEnvironment dfe, String name, Class<E> cls) {
        return objectMapper.convertValue(dfe.getArgument(name), cls);
    }

    protected <E> E arg(DataFetchingEnvironment dfe, String name, TypeReference<E> typeRef) {
        return objectMapper.convertValue(dfe.getArgument(name), typeRef);
    }
}
