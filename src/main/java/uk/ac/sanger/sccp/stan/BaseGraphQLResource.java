package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Logger log = LoggerFactory.getLogger(BaseGraphQLResource.class);

    final ObjectMapper objectMapper;
    final AuthenticationComponent authComp;
    final UserRepo userRepo;

    protected BaseGraphQLResource(ObjectMapper objectMapper, AuthenticationComponent authComp, UserRepo userRepo) {
        this.objectMapper = objectMapper;
        this.authComp = authComp;
        this.userRepo = userRepo;
    }

    /**
     * Gets the user for this request and checks that they have the indicated user role.
     * @param dfe the DataFetchingEnvironment for the request
     * @param role the required role for the request
     * @return the user if a valid user is identified
     * @exception AuthenticationCredentialsNotFoundException if no user is identified
     * @exception InsufficientAuthenticationException if the user does not have the required role
     * @exception javax.persistence.EntityNotFoundException possible from {@link #getUser}
     */
    protected User checkUser(DataFetchingEnvironment dfe, User.Role role) {
        User user = getUser(dfe);
        if (user==null) {
            throw new AuthenticationCredentialsNotFoundException("Not logged in");
        }
        if (!user.hasRole(role)) {
            throw new InsufficientAuthenticationException("Requires role: "+role);
        }
        return user;
    }

    /**
     * Gets the user for this request, either from the session or from the api key.
     * Returns null if no user can be identified.
     * @param dfe the DataFetchingEnvironment for the request, which should include the request context
     * @return the user if one was identified; null if no user was identified
     * @exception javax.persistence.EntityNotFoundException if the user linked with the api key does not exist
     */
    protected User getUser(DataFetchingEnvironment dfe) {
        Authentication auth = authComp.getAuthentication();
        if (auth != null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof User) {
                return (User) principal;
            }
        }
        StanRequestContext context = dfe.getContext();
        if (context!=null && context.getUsername()!=null) {
            log.info("Processing request using API key with user {}.", context.getUsername());
            return userRepo.getByUsername(context.getUsername());
        }
        return null;
    }

    protected <E> E arg(DataFetchingEnvironment dfe, String name, Class<E> cls) {
        return objectMapper.convertValue(dfe.getArgument(name), cls);
    }

    protected <E> E arg(DataFetchingEnvironment dfe, String name, TypeReference<E> typeRef) {
        return objectMapper.convertValue(dfe.getArgument(name), typeRef);
    }
}
