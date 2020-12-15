package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationRequest;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationResult;
import uk.ac.sanger.sccp.stan.request.plan.PlanRequest;
import uk.ac.sanger.sccp.stan.request.plan.PlanResult;
import uk.ac.sanger.sccp.stan.service.LDAPService;
import uk.ac.sanger.sccp.stan.service.label.print.LabelPrintService;
import uk.ac.sanger.sccp.stan.service.operation.plan.PlanService;
import uk.ac.sanger.sccp.stan.service.register.RegisterService;

import java.util.*;

/**
 * @author dr6
 */
@Component
public class GraphQLMutation {
    final ObjectMapper objectMapper;
    final AuthenticationComponent authComp;

    final LDAPService ldapService;
    final SessionConfig sessionConfig;
    final RegisterService registerService;
    final PlanService planService;
    final LabelPrintService labelPrintService;

    final UserRepo userRepo;

    @Autowired
    public GraphQLMutation(ObjectMapper objectMapper, AuthenticationComponent authComp,
                           LDAPService ldapService, SessionConfig sessionConfig,
                           RegisterService registerService, PlanService planService,
                           LabelPrintService labelPrintService, UserRepo userRepo) {
        this.objectMapper = objectMapper;
        this.authComp = authComp;
        this.ldapService = ldapService;
        this.sessionConfig = sessionConfig;
        this.registerService = registerService;
        this.planService = planService;
        this.labelPrintService = labelPrintService;
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
            authComp.setAuthentication(authentication, sessionConfig.getMaxInactiveMinutes());
            return new LoginResult("OK", optUser.get());
        };
    }


    public DataFetcher<String> logOut() {
        return dataFetchingEnvironment -> {
            authComp.setAuthentication(null, 0);
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

    public DataFetcher<PlanResult> recordPlan() {
        return dfe -> {
            User user = checkUser();
            PlanRequest request = arg(dfe, "request", PlanRequest.class);
            return planService.recordPlan(user, request);
        };
    }

    public DataFetcher<String> printLabware() {
        return dfe -> {
            User user = checkUser();
            List<String> barcodes = dfe.getArgument("barcodes");
            String printerName = dfe.getArgument("printer");
            labelPrintService.printLabwareBarcodes(user, printerName, barcodes);
            return "OK";
        };
    }

    public DataFetcher<ConfirmOperationResult> confirmOperation() {
        return dfe -> {
            User user = checkUser();
            ConfirmOperationRequest request = arg(dfe, "request", ConfirmOperationRequest.class);

            return new ConfirmOperationResult();
        };
    }

    private User checkUser() {
        Authentication auth = authComp.getAuthentication();
        if (auth==null || auth instanceof AnonymousAuthenticationToken || auth.getPrincipal()==null) {
            throw new AuthenticationCredentialsNotFoundException("Not logged in");
        }
        String username = auth.getPrincipal().toString();
        return userRepo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
    }

    private <E> E arg(DataFetchingEnvironment dfe, String name, Class<E> cls) {
        return objectMapper.convertValue(dfe.getArgument(name), cls);
    }

    private <E> E arg(DataFetchingEnvironment dfe, String name, TypeReference<E> typeRef) {
        return objectMapper.convertValue(dfe.getArgument(name), typeRef);
    }
}
