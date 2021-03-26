package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationRequest;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationResult;
import uk.ac.sanger.sccp.stan.request.plan.PlanRequest;
import uk.ac.sanger.sccp.stan.request.plan.PlanResult;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.extract.ExtractService;
import uk.ac.sanger.sccp.stan.service.label.print.LabelPrintService;
import uk.ac.sanger.sccp.stan.service.operation.confirm.ConfirmOperationService;
import uk.ac.sanger.sccp.stan.service.operation.plan.PlanService;
import uk.ac.sanger.sccp.stan.service.register.RegisterService;
import uk.ac.sanger.sccp.stan.service.register.SectionRegisterService;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Component
public class GraphQLMutation extends BaseGraphQLResource {
    Logger log = LoggerFactory.getLogger(GraphQLMutation.class);
    final LDAPService ldapService;
    final SessionConfig sessionConfig;
    final RegisterService registerService;
    final SectionRegisterService sectionRegisterService;
    final PlanService planService;
    final LabelPrintService labelPrintService;
    final ConfirmOperationService confirmOperationService;
    final ReleaseService releaseService;
    final ExtractService extractService;
    final DestructionService destructionService;
    final SlotCopyService slotCopyService;

    @Autowired
    public GraphQLMutation(ObjectMapper objectMapper, AuthenticationComponent authComp,
                           LDAPService ldapService, SessionConfig sessionConfig,
                           RegisterService registerService, SectionRegisterService sectionRegisterService, PlanService planService,
                           LabelPrintService labelPrintService,
                           ConfirmOperationService confirmOperationService,
                           UserRepo userRepo, ReleaseService releaseService, ExtractService extractService,
                           DestructionService destructionService, SlotCopyService slotCopyService) {
        super(objectMapper, authComp, userRepo);
        this.ldapService = ldapService;
        this.sessionConfig = sessionConfig;
        this.registerService = registerService;
        this.sectionRegisterService = sectionRegisterService;
        this.planService = planService;
        this.labelPrintService = labelPrintService;
        this.confirmOperationService = confirmOperationService;
        this.releaseService = releaseService;
        this.extractService = extractService;
        this.destructionService = destructionService;
        this.slotCopyService = slotCopyService;
    }

    private void logRequest(String name, User user, Object request) {
        if (log.isInfoEnabled()) {
            log.info("{} requested by {}: {}", name, (user==null ? null : repr(user.getUsername())), request);
        }
    }

    public DataFetcher<LoginResult> logIn() {
        return dataFetchingEnvironment -> {
            String username = dataFetchingEnvironment.getArgument("username");
            if (log.isInfoEnabled()) {
                log.info("Login attempt by {}", repr(username));
            }
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
            log.info("Login succeeded for user {}", optUser.get());
            return new LoginResult("OK", optUser.get());
        };
    }

    private String loggedInUsername() {
        var auth = authComp.getAuthentication();
        if (auth != null) {
            var princ = auth.getPrincipal();
            if (princ != null) {
                return princ.toString();
            }
        }
        return null;
    }

    public DataFetcher<String> logOut() {
        return dataFetchingEnvironment -> {
            if (log.isInfoEnabled()) {
                log.info("Logout requested by {}", repr(loggedInUsername()));
            }
            authComp.setAuthentication(null, 0);
            return "OK";
        };
    }

    public DataFetcher<RegisterResult> register() {
        return dfe -> {
            User user = checkUser();
            RegisterRequest request = arg(dfe, "request", RegisterRequest.class);
            logRequest("Register", user, request);
            return registerService.register(request, user);
        };
    }

    public DataFetcher<RegisterResult> sectionRegister() {
        return dfe -> {
            User user = checkUser();
            SectionRegisterRequest request = arg(dfe, "request", SectionRegisterRequest.class);
            logRequest("Section register", user, request);
            return sectionRegisterService.register(user, request);
        };
    }

    public DataFetcher<PlanResult> recordPlan() {
        return dfe -> {
            User user = checkUser();
            PlanRequest request = arg(dfe, "request", PlanRequest.class);
            logRequest("Record plan", user, request);
            return planService.recordPlan(user, request);
        };
    }

    public DataFetcher<String> printLabware() {
        return dfe -> {
            User user = checkUser();
            List<String> barcodes = dfe.getArgument("barcodes");
            String printerName = dfe.getArgument("printer");
            if (log.isInfoEnabled()) {
                logRequest("Print labware", user, "Printer: " + repr(printerName) + ", barcodes: " + barcodes);
            }
            labelPrintService.printLabwareBarcodes(user, printerName, barcodes);
            return "OK";
        };
    }

    public DataFetcher<ConfirmOperationResult> confirmOperation() {
        return dfe -> {
            User user = checkUser();
            ConfirmOperationRequest request = arg(dfe, "request", ConfirmOperationRequest.class);
            logRequest("Confirm operation", user, request);
            return confirmOperationService.confirmOperation(user, request);
        };
    }

    public DataFetcher<ReleaseResult> release() {
        return dfe -> {
            User user = checkUser();
            ReleaseRequest request = arg(dfe, "request", ReleaseRequest.class);
            logRequest("Release", user, request);
            return releaseService.releaseAndUnstore(user, request);
        };
    }

    public DataFetcher<OperationResult> extract() {
        return dfe -> {
            User user = checkUser();
            ExtractRequest request = arg(dfe, "request", ExtractRequest.class);
            logRequest("Extract", user, request);
            return extractService.extractAndUnstore(user, request);
        };
    }

    public DataFetcher<DestroyResult> destroy() {
        return dfe -> {
            User user = checkUser();
            DestroyRequest request = arg(dfe, "request", DestroyRequest.class);
            logRequest("Destroy", user, request);
            return destructionService.destroyAndUnstore(user, request);
        };
    }

    public DataFetcher<OperationResult> slotCopy() {
        return dfe -> {
            User user = checkUser();
            SlotCopyRequest request = arg(dfe, "request", SlotCopyRequest.class);
            logRequest("SlotCopy", user, request);
            return slotCopyService.perform(user, request);
        };
    }
}
