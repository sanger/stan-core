package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

/**
 * @author dr6
 */
@Component
public class GraphQLDataFetchers {
    final ObjectMapper objectMapper;

    final SessionConfig sessionConfig;
    final UserRepo userRepo;
    final TissueTypeRepo tissueTypeRepo;
    final LabwareTypeRepo labwareTypeRepo;
    final MediumRepo mediumRepo;
    final MouldSizeRepo mouldSizeRepo;
    final HmdmcRepo hmdmcRepo;

    @Autowired
    public GraphQLDataFetchers(ObjectMapper objectMapper, SessionConfig sessionConfig,
                               UserRepo userRepo,
                               TissueTypeRepo tissueTypeRepo, LabwareTypeRepo labwareTypeRepo,
                               MediumRepo mediumRepo, MouldSizeRepo mouldSizeRepo, HmdmcRepo hmdmcRepo) {
        this.objectMapper = objectMapper;
        this.sessionConfig = sessionConfig;
        this.userRepo = userRepo;
        this.tissueTypeRepo = tissueTypeRepo;
        this.labwareTypeRepo = labwareTypeRepo;
        this.mediumRepo = mediumRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.hmdmcRepo = hmdmcRepo;
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

    private boolean requestsField(DataFetchingEnvironment dfe, String childName) {
        return dfe.getField().getSelectionSet().getChildren().stream()
                .anyMatch(f -> ((Field) f).getName().equals(childName));
    }
}
