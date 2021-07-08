package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.CommentAdminService;
import uk.ac.sanger.sccp.stan.service.FindService;
import uk.ac.sanger.sccp.stan.service.label.print.LabelPrintService;
import uk.ac.sanger.sccp.stan.service.operation.plan.PlanService;

import javax.persistence.EntityNotFoundException;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

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
    final CommentAdminService commentAdminService;
    final PlanService planService;

    @Autowired
    public GraphQLDataFetchers(ObjectMapper objectMapper, AuthenticationComponent authComp, UserRepo userRepo,
                               SessionConfig sessionConfig,
                               TissueTypeRepo tissueTypeRepo, LabwareTypeRepo labwareTypeRepo,
                               MediumRepo mediumRepo, FixativeRepo fixativeRepo, MouldSizeRepo mouldSizeRepo,
                               SpeciesRepo speciesRepo, HmdmcRepo hmdmcRepo, LabwareRepo labwareRepo, CommentRepo commentRepo,
                               ReleaseDestinationRepo releaseDestinationRepo, ReleaseRecipientRepo releaseRecipientRepo,
                               DestructionReasonRepo destructionReasonRepo,
                               LabelPrintService labelPrintService, FindService findService,
                               CommentAdminService commentAdminService, PlanService planService) {
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
        this.commentAdminService = commentAdminService;
        this.planService = planService;
    }

    public DataFetcher<User> getUser() {
        return this::getUser;
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
        return allOrEnabled(speciesRepo::findAll, speciesRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<Hmdmc>> getHmdmcs() {
        return allOrEnabled(hmdmcRepo::findAll, hmdmcRepo::findAllByEnabled);
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
            boolean includeDisabled = argOrFalse(dfe, "includeDisabled");
            return commentAdminService.getComments(category, includeDisabled);
        };
    }

    public DataFetcher<Iterable<ReleaseDestination>> getReleaseDestinations() {
        return allOrEnabled(releaseDestinationRepo::findAll, releaseDestinationRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<ReleaseRecipient>> getReleaseRecipients() {
        return allOrEnabled(releaseRecipientRepo::findAll, releaseRecipientRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<DestructionReason>> getDestructionReasons() {
        return allOrEnabled(destructionReasonRepo::findAll, destructionReasonRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<User>> getUsers() {
        return dfe -> {
            boolean includeDisabled = argOrFalse(dfe,"includeDisabled");
            Iterable<User> users = userRepo.findAll();
            if (includeDisabled) {
                return users;
            }
            return StreamSupport.stream(users.spliterator(), false)
                        .filter(user -> user.getRole()!= User.Role.disabled)
                        .collect(toList());
        };
    }

    public DataFetcher<FindResult> find() {
        return dfe -> {
            FindRequest request = arg(dfe, "request", FindRequest.class);
            return findService.find(request);
        };
    }

    public DataFetcher<PlanData> getPlanData() {
        return dfe -> planService.getPlanData(dfe.getArgument("barcode"));
    }

    private boolean argOrFalse(DataFetchingEnvironment dfe, String argName) {
        Boolean arg = dfe.getArgument(argName);
        return Boolean.TRUE.equals(arg);
    }

    private <E> DataFetcher<Iterable<E>> allOrEnabled(Supplier<? extends Iterable<E>> findAll,
                                                      BoolObjFunction<? extends Iterable<E>> findByEnabled) {
        return dfe -> {
            boolean includeDisabled = argOrFalse(dfe, "includeDisabled");
            return includeDisabled ? findAll.get() : findByEnabled.apply(true);
        };
    }

    private boolean requestsField(DataFetchingEnvironment dfe, String childName) {
        return dfe.getField().getSelectionSet().getChildren().stream()
                .anyMatch(f -> ((Field) f).getName().equals(childName));
    }

    @FunctionalInterface
    private interface BoolObjFunction<E> {
        E apply(boolean arg);
    }
}
