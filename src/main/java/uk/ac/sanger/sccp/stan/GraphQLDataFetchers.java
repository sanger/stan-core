package uk.ac.sanger.sccp.stan;

import graphql.language.Field;
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
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.LoginResult;
import uk.ac.sanger.sccp.stan.request.RegisterResult;
import uk.ac.sanger.sccp.stan.service.LDAPService;
import uk.ac.sanger.sccp.stan.service.register.RegisterService;

import java.util.ArrayList;
import java.util.Optional;

/**
 * @author dr6
 */
@Component
public class GraphQLDataFetchers {
    final LDAPService ldapService;
    final SessionConfig sessionConfig;
    final RegisterService registerService;

    final UserRepo userRepo;
    final TissueTypeRepo tissueTypeRepo;
    final LabwareTypeRepo labwareTypeRepo;
    final MediumRepo mediumRepo;
    final MouldSizeRepo mouldSizeRepo;
    final HmdmcRepo hmdmcRepo;

    @Autowired
    public GraphQLDataFetchers(LDAPService ldapService, SessionConfig sessionConfig,
                               RegisterService registerService,
                               UserRepo userRepo,
                               TissueTypeRepo tissueTypeRepo, LabwareTypeRepo labwareTypeRepo,
                               MediumRepo mediumRepo, MouldSizeRepo mouldSizeRepo, HmdmcRepo hmdmcRepo) {
        this.ldapService = ldapService;
        this.sessionConfig = sessionConfig;
        this.registerService = registerService;
        this.userRepo = userRepo;
        this.tissueTypeRepo = tissueTypeRepo;
        this.labwareTypeRepo = labwareTypeRepo;
        this.mediumRepo = mediumRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.hmdmcRepo = hmdmcRepo;
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

    public DataFetcher<Iterable<TissueType>> getTissueTypes() {
        return dfe -> tissueTypeRepo.findAll();
    }

    public DataFetcher<Iterable<LabwareType>> getLabwareTypes() {
        return dfe -> labwareTypeRepo.findAll();
    }

    public DataFetcher<Iterable<Medium>> getMediums() {
        return dfe -> mediumRepo.findAll();
    }

    public DataFetcher<Iterable<MouldSize>> getMouldSizes() {
        return dfe -> mouldSizeRepo.findAll();
    }

    public DataFetcher<Iterable<Hmdmc>> getHmdmcs() {
        return dfe -> hmdmcRepo.findAll();
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
            return registerService.register(dfe.getArgument("request"), user);
        };
    }

    private boolean requestsField(DataFetchingEnvironment dfe, String childName) {
        return dfe.getField().getSelectionSet().getChildren().stream()
                .anyMatch(f -> ((Field) f).getName().equals(childName));
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
}
