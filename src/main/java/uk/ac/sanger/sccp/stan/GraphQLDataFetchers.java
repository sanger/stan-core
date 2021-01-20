package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.label.print.LabelPrintService;

import javax.persistence.EntityNotFoundException;

/**
 * @author dr6
 */
@Component
public class GraphQLDataFetchers extends BaseGraphQLResource {

    final SessionConfig sessionConfig;
    final TissueTypeRepo tissueTypeRepo;
    final LabwareTypeRepo labwareTypeRepo;
    final MediumRepo mediumRepo;
    final FixativeRepo fixativeRepo;
    final MouldSizeRepo mouldSizeRepo;
    final HmdmcRepo hmdmcRepo;
    final LabwareRepo labwareRepo;
    final CommentRepo commentRepo;
    final LabelPrintService labelPrintService;

    @Autowired
    public GraphQLDataFetchers(ObjectMapper objectMapper, AuthenticationComponent authComp, UserRepo userRepo,
                               SessionConfig sessionConfig,
                               TissueTypeRepo tissueTypeRepo, LabwareTypeRepo labwareTypeRepo,
                               MediumRepo mediumRepo, FixativeRepo fixativeRepo, MouldSizeRepo mouldSizeRepo,
                               HmdmcRepo hmdmcRepo, LabwareRepo labwareRepo, CommentRepo commentRepo,
                               LabelPrintService labelPrintService) {
        super(objectMapper, authComp, userRepo);
        this.sessionConfig = sessionConfig;
        this.tissueTypeRepo = tissueTypeRepo;
        this.labwareTypeRepo = labwareTypeRepo;
        this.mediumRepo = mediumRepo;
        this.fixativeRepo = fixativeRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.labwareRepo = labwareRepo;
        this.commentRepo = commentRepo;
        this.labelPrintService = labelPrintService;
    }

    public DataFetcher<User> getUser() {
        return dataFetchingEnvironment -> {
            Authentication auth = authComp.getAuthentication();
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

    public DataFetcher<Iterable<Fixative>> getFixatives() {
        return dfe -> fixativeRepo.findAll();
    }

    public DataFetcher<Labware> findLabwareByBarcode() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            if (barcode==null || barcode.isEmpty()) {
                throw new IllegalArgumentException("No barcode supplied.");
            }
            return labwareRepo.findByBarcode(barcode)
                    .orElseThrow(() -> new EntityNotFoundException("No labware found with barcode: "+barcode));
        };
    }

    public DataFetcher<Iterable<Printer>> findPrinters() {
        return dfe -> {
            String labelTypeName = dfe.getArgument("labelType");
            return labelPrintService.findPrinters(labelTypeName);
        };
    }

    public DataFetcher<Iterable<Comment>> getComments() {
        return dfe -> {
            String category = dfe.getArgument("category");
            if (category==null) {
                return commentRepo.findAllByEnabled(true);
            }
            return commentRepo.findAllByCategoryAndEnabled(category, true);
        };
    }

    private boolean requestsField(DataFetchingEnvironment dfe, String childName) {
        return dfe.getField().getSelectionSet().getChildren().stream()
                .anyMatch(f -> ((Field) f).getName().equals(childName));
    }
}
