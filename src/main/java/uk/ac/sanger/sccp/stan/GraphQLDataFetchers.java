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
import uk.ac.sanger.sccp.stan.request.FindRequest;
import uk.ac.sanger.sccp.stan.request.FindResult;
import uk.ac.sanger.sccp.stan.service.FindService;
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
    final SpeciesRepo speciesRepo;
    final HmdmcRepo hmdmcRepo;
    final LabwareRepo labwareRepo;
    final CommentRepo commentRepo;
    final ReleaseDestinationRepo releaseDestinationRepo;
    final ReleaseRecipientRepo releaseRecipientRepo;
    final DestructionReasonRepo destructionReasonRepo;
    final LabelPrintService labelPrintService;
    final FindService findService;

    @Autowired
    public GraphQLDataFetchers(ObjectMapper objectMapper, AuthenticationComponent authComp, UserRepo userRepo,
                               SessionConfig sessionConfig,
                               TissueTypeRepo tissueTypeRepo, LabwareTypeRepo labwareTypeRepo,
                               MediumRepo mediumRepo, FixativeRepo fixativeRepo, MouldSizeRepo mouldSizeRepo,
                               SpeciesRepo speciesRepo, HmdmcRepo hmdmcRepo, LabwareRepo labwareRepo, CommentRepo commentRepo,
                               ReleaseDestinationRepo releaseDestinationRepo, ReleaseRecipientRepo releaseRecipientRepo,
                               DestructionReasonRepo destructionReasonRepo,
                               LabelPrintService labelPrintService, FindService findService) {
        super(objectMapper, authComp, userRepo);
        this.sessionConfig = sessionConfig;
        this.tissueTypeRepo = tissueTypeRepo;
        this.labwareTypeRepo = labwareTypeRepo;
        this.mediumRepo = mediumRepo;
        this.fixativeRepo = fixativeRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.speciesRepo = speciesRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.labwareRepo = labwareRepo;
        this.commentRepo = commentRepo;
        this.releaseDestinationRepo = releaseDestinationRepo;
        this.releaseRecipientRepo = releaseRecipientRepo;
        this.destructionReasonRepo = destructionReasonRepo;
        this.labelPrintService = labelPrintService;
        this.findService = findService;
    }

    public DataFetcher<User> getUser() {
        return dataFetchingEnvironment -> {
            Authentication auth = authComp.getAuthentication();
            if (auth==null || auth instanceof AnonymousAuthenticationToken || auth.getPrincipal()==null) {
                return null;
            }
            Object princ = auth.getPrincipal();
            if (princ instanceof User) {
                return (User) princ;
            }
            return null;
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

    public DataFetcher<Iterable<Species>> getSpecies() {
        return dfe -> speciesRepo.findAll();
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

    public DataFetcher<Iterable<ReleaseDestination>> getReleaseDestinations() {
        return dfe -> releaseDestinationRepo.findAllByEnabled(true);
    }

    public DataFetcher<Iterable<ReleaseRecipient>> getReleaseRecipients() {
        return dfe -> releaseRecipientRepo.findAllByEnabled(true);
    }

    public DataFetcher<Iterable<DestructionReason>> getDestructionReasons() {
        return dfe -> destructionReasonRepo.findAllByEnabled(true);
    }

    public DataFetcher<FindResult> find() {
        return dfe -> {
            FindRequest request = arg(dfe, "request", FindRequest.class);
            return findService.find(request);
        };
    }

    private boolean requestsField(DataFetchingEnvironment dfe, String childName) {
        return dfe.getField().getSelectionSet().getChildren().stream()
                .anyMatch(f -> ((Field) f).getName().equals(childName));
    }
}
