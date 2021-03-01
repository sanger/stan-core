package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

    protected User checkUser() {
        Authentication auth = authComp.getAuthentication();
        if (auth==null || auth instanceof AnonymousAuthenticationToken || auth.getPrincipal()==null) {
            throw new AuthenticationCredentialsNotFoundException("Not logged in");
        }
        String username = auth.getPrincipal().toString();
        return userRepo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
    }

    protected <E> E arg(DataFetchingEnvironment dfe, String name, Class<E> cls) {
        return objectMapper.convertValue(dfe.getArgument(name), cls);
    }

    protected <E> E arg(DataFetchingEnvironment dfe, String name, TypeReference<E> typeRef) {
        return objectMapper.convertValue(dfe.getArgument(name), typeRef);
    }
}
