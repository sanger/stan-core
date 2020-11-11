package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.LDAPService;
import uk.ac.sanger.sccp.stan.service.register.RegisterService;

import java.util.ArrayList;
import java.util.Optional;

/**
 * @author dr6
 */
@Component
public class GraphQLMutation {
    final ObjectMapper objectMapper;

    final LDAPService ldapService;
    final SessionConfig sessionConfig;
    final RegisterService registerService;

    final UserRepo userRepo;

    @Autowired
    public GraphQLMutation(ObjectMapper objectMapper, LDAPService ldapService, SessionConfig sessionConfig,
                               RegisterService registerService,
                               UserRepo userRepo) {
        this.objectMapper = objectMapper;
        this.ldapService = ldapService;
        this.sessionConfig = sessionConfig;
        this.registerService = registerService;
        this.userRepo = userRepo;
    }


    public DataFetcher<LoginResult> logIn() {
        return dataFetchingEnvironment -> {
            String username = dataFetchingEnvironment.getArgument("username");
            Optional<User> optUser = userRepo.findByUsername(username);
            if (optUser.isEmpty()) {
                return new LoginResult("Username not in database.", null);
            }
            String password = dataFetchingEnvironment.getArgument("password");
            if (!ldapService.verifyCredentials(username, password)) {
                return new LoginResult("Login failed", null);
            }
            Authentication authentication = new UsernamePasswordAuthenticationToken(username, password, new ArrayList<>());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            attr.getRequest().getSession().setMaxInactiveInterval(60 * this.sessionConfig.getMaxInactiveMinutes());
            return new LoginResult("OK", optUser.get());
        };
    }


    public DataFetcher<String> logOut() {
        return dataFetchingEnvironment -> {
            SecurityContextHolder.getContext().setAuthentication(null);
            return "OK";
        };
    }

    public DataFetcher<RegisterResult> register() {
        return dfe -> {
            User user = checkUser();
            RegisterRequest request = arg(dfe, "request", RegisterRequest.class);
            return registerService.register(request, user);
        };
    }

    private User checkUser() {
        SecurityContext sc = SecurityContextHolder.getContext();
        Authentication auth = (sc==null ? null : sc.getAuthentication());
        if (auth==null || auth instanceof AnonymousAuthenticationToken || auth.getPrincipal()==null) {
            throw new AuthenticationCredentialsNotFoundException("Not logged in");
        }
        String username = auth.getPrincipal().toString();
        return userRepo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
    }

    private <E> E arg(DataFetchingEnvironment dfe, String name, Class<E> cls) {
        return objectMapper.convertValue(dfe.getArgument(name), cls);
    }
}
