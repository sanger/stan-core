package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.config.VersionInfo;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.LabwareRoi.RoiResult;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.extract.ExtractResultQueryService;
import uk.ac.sanger.sccp.stan.service.flag.FlagLookupService;
import uk.ac.sanger.sccp.stan.service.graph.GraphService;
import uk.ac.sanger.sccp.stan.service.history.HistoryService;
import uk.ac.sanger.sccp.stan.service.label.print.LabelPrintService;
import uk.ac.sanger.sccp.stan.service.operation.AnalyserServiceImp;
import uk.ac.sanger.sccp.stan.service.operation.RecentOpService;
import uk.ac.sanger.sccp.stan.service.operation.plan.PlanService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.stan.service.work.WorkSummaryService;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Component
public class GraphQLDataFetchers extends BaseGraphQLResource {

    final SessionConfig sessionConfig;
    final VersionInfo versionInfo;
    final TissueTypeRepo tissueTypeRepo;
    final LabwareTypeRepo labwareTypeRepo;
    final MediumRepo mediumRepo;
    final FixativeRepo fixativeRepo;
    final SpeciesRepo speciesRepo;
    final HmdmcRepo hmdmcRepo;
    final BioRiskRepo bioRiskRepo;
    final LabwareRepo labwareRepo;
    final ReleaseDestinationRepo releaseDestinationRepo;
    final ReleaseRecipientRepo releaseRecipientRepo;
    final DestructionReasonRepo destructionReasonRepo;
    final ProjectRepo projectRepo;
    final ProgramRepo programRepo;
    final CostCodeRepo costCodeRepo;
    final DnapStudyRepo dnapStudyRepo;
    final SolutionRepo solutionRepo;
    final OmeroProjectRepo omeroProjectRepo;
    final ProbePanelRepo probePanelRepo;
    final WorkTypeRepo workTypeRepo;
    final WorkRepo workRepo;
    final ReagentPlateRepo reagentPlateRepo;
    final LabelPrintService labelPrintService;
    final FindService findService;
    final CommentAdminService commentAdminService;
    final EquipmentAdminService equipmentAdminService;
    final HistoryService historyService;
    final WorkProgressService workProgressService;
    final PlanService planService;
    final StainService stainService;
    final ExtractResultQueryService extractResultQueryService;
    final PassFailQueryService passFailQueryService;
    final WorkService workService;
    final VisiumPermDataService visiumPermDataService;
    final NextReplicateService nextReplicateService;
    final WorkSummaryService workSummaryService;
    final LabwareService labwareService;
    final FileStoreService fileStoreService;
    final SlotRegionService slotRegionService;
    final RoiService roiService;
    final RecentOpService recentOpService;
    final CleanedOutSlotService cleanedOutSlotService;
    final FlagLookupService flagLookupService;
    final MeasurementService measurementService;
    final GraphService graphService;
    final CommentRepo commentRepo;
    final AnalyserScanDataService analyserScanDataService;
    final LabwareNoteService lwNoteService;

    @Autowired
    public GraphQLDataFetchers(ObjectMapper objectMapper, AuthenticationComponent authComp, UserRepo userRepo,
                               SessionConfig sessionConfig, VersionInfo versionInfo,
                               TissueTypeRepo tissueTypeRepo, LabwareTypeRepo labwareTypeRepo,
                               MediumRepo mediumRepo, FixativeRepo fixativeRepo,
                               SpeciesRepo speciesRepo, HmdmcRepo hmdmcRepo, BioRiskRepo bioRiskRepo, LabwareRepo labwareRepo,
                               ReleaseDestinationRepo releaseDestinationRepo, ReleaseRecipientRepo releaseRecipientRepo,
                               DestructionReasonRepo destructionReasonRepo, ProjectRepo projectRepo,
                               ProgramRepo programRepo, CostCodeRepo costCodeRepo, DnapStudyRepo dnapStudyRepo,
                               SolutionRepo solutionRepo, OmeroProjectRepo omeroProjectRepo,
                               ProbePanelRepo probePanelRepo, WorkTypeRepo workTypeRepo, WorkRepo workRepo,
                               ReagentPlateRepo reagentPlateRepo,
                               LabelPrintService labelPrintService, FindService findService,
                               CommentAdminService commentAdminService, EquipmentAdminService equipmentAdminService,
                               HistoryService historyService, WorkProgressService workProgressService, PlanService planService,
                               StainService stainService, ExtractResultQueryService extractResultQueryService,
                               PassFailQueryService passFailQueryService,
                               WorkService workService, VisiumPermDataService visiumPermDataService,
                               NextReplicateService nextReplicateService, WorkSummaryService workSummaryService,
                               LabwareService labwareService, FileStoreService fileStoreService,
                               SlotRegionService slotRegionService, RoiService roiService, RecentOpService recentOpService,
                               CleanedOutSlotService cleanedOutSlotService,
                               FlagLookupService flagLookupService, MeasurementService measurementService,
                               GraphService graphService, CommentRepo commentRepo,
                               AnalyserScanDataService analyserScanDataService, LabwareNoteService lwNoteService) {
        super(objectMapper, authComp, userRepo);
        this.sessionConfig = sessionConfig;
        this.versionInfo = versionInfo;
        this.tissueTypeRepo = tissueTypeRepo;
        this.labwareTypeRepo = labwareTypeRepo;
        this.mediumRepo = mediumRepo;
        this.fixativeRepo = fixativeRepo;
        this.speciesRepo = speciesRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.bioRiskRepo = bioRiskRepo;
        this.labwareRepo = labwareRepo;
        this.dnapStudyRepo = dnapStudyRepo;
        this.solutionRepo = solutionRepo;
        this.omeroProjectRepo = omeroProjectRepo;
        this.probePanelRepo = probePanelRepo;
        this.reagentPlateRepo = reagentPlateRepo;
        this.equipmentAdminService = equipmentAdminService;
        this.releaseDestinationRepo = releaseDestinationRepo;
        this.releaseRecipientRepo = releaseRecipientRepo;
        this.destructionReasonRepo = destructionReasonRepo;
        this.projectRepo = projectRepo;
        this.programRepo = programRepo;
        this.costCodeRepo = costCodeRepo;
        this.workTypeRepo = workTypeRepo;
        this.workRepo = workRepo;
        this.labelPrintService = labelPrintService;
        this.findService = findService;
        this.commentAdminService = commentAdminService;
        this.historyService = historyService;
        this.workProgressService = workProgressService;
        this.planService = planService;
        this.stainService = stainService;
        this.extractResultQueryService = extractResultQueryService;
        this.passFailQueryService = passFailQueryService;
        this.workService = workService;
        this.visiumPermDataService = visiumPermDataService;
        this.nextReplicateService = nextReplicateService;
        this.workSummaryService = workSummaryService;
        this.labwareService = labwareService;
        this.fileStoreService = fileStoreService;
        this.slotRegionService = slotRegionService;
        this.roiService = roiService;
        this.recentOpService = recentOpService;
        this.cleanedOutSlotService = cleanedOutSlotService;
        this.flagLookupService = flagLookupService;
        this.measurementService = measurementService;
        this.graphService = graphService;
        this.commentRepo = commentRepo;
        this.analyserScanDataService = analyserScanDataService;
        this.lwNoteService = lwNoteService;
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

    public DataFetcher<Iterable<Species>> getSpecies() {
        return allOrEnabled(speciesRepo::findAll, speciesRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<Hmdmc>> getHmdmcs() {
        return allOrEnabled(hmdmcRepo::findAll, hmdmcRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<BioRisk>> getBioRisks() {
        return allOrEnabled(bioRiskRepo::findAll, bioRiskRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<Fixative>> getFixatives() {
        return allOrEnabled(fixativeRepo::findAll, fixativeRepo::findAllByEnabled);
    }

    public DataFetcher<Labware> findLabwareByBarcode() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            if (nullOrEmpty(barcode)) {
                throw new IllegalArgumentException("No barcode supplied.");
            }
            return labwareRepo.getByBarcode(barcode);
        };
    }

    public DataFetcher<LabwareFlagged> findLabwareFlagged() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            if (nullOrEmpty(barcode)) {
                throw new IllegalArgumentException("No barcode supplied.");
            }
            Labware lw = labwareRepo.getByBarcode(barcode);
            if (requestsField(dfe, "flagged")) {
                return flagLookupService.getLabwareFlagged(lw);
            }
            return new LabwareFlagged(lw, false);
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

    public DataFetcher<Iterable<Equipment>> getEquipments() {
        return dfe -> {
            String category = dfe.getArgument("category");
            boolean includeDisabled = argOrFalse(dfe, "includeDisabled");
            return equipmentAdminService.getEquipment(category, includeDisabled);
        };
    }

    public DataFetcher<Iterable<ReleaseDestination>> getReleaseDestinations() {
        return allOrEnabled(releaseDestinationRepo::findAll, releaseDestinationRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<ReleaseRecipient>> getReleaseRecipients() {
        return allOrEnabled(releaseRecipientRepo::findAll, releaseRecipientRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<ReleaseFileOption>> getReleaseColumnOptions() {
        return dfe -> Arrays.asList(ReleaseFileOption.values());
    }

    public DataFetcher<Iterable<DestructionReason>> getDestructionReasons() {
        return allOrEnabled(destructionReasonRepo::findAll, destructionReasonRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<Project>> getProjects() {
        return allOrEnabled(projectRepo::findAll, projectRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<Program>> getPrograms() {
        return allOrEnabled(programRepo::findAll, programRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<CostCode>> getCostCodes() {
        return allOrEnabled(costCodeRepo::findAll, costCodeRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<DnapStudy>> getDnapStudies() {
        return allOrEnabled(dnapStudyRepo::findAll, dnapStudyRepo::findAllByEnabled);
    }

    public DataFetcher<DnapStudy> getDnapStudy() {
        return dfe -> {
            Integer ssId = dfe.getArgument("ssId");
            return dnapStudyRepo.findBySsId(ssId).orElse(null);
        };
    }

    public DataFetcher<Iterable<Solution>> getSolutions() {
        return allOrEnabled(solutionRepo::findAll, solutionRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<OmeroProject>> getOmeroProjects() {
        return allOrEnabled(omeroProjectRepo::findAll, omeroProjectRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<SlotRegion>> getSlotRegions() {
        return dfe -> slotRegionService.loadSlotRegions(argOrFalse(dfe, "includeDisabled"));
    }

    public DataFetcher<Iterable<ProbePanel>> getProbePanels() {
        return allOrEnabled(probePanelRepo::findAll, probePanelRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<SamplePositionResult>> getSamplePositions() {
        return dfe -> slotRegionService.loadSamplePositionResultsForLabware((String)dfe.getArgument("labwareBarcode"));
    }

    public DataFetcher<Iterable<WorkType>> getWorkTypes() {
        return allOrEnabled(workTypeRepo::findAll, workTypeRepo::findAllByEnabled);
    }

    public DataFetcher<Iterable<Work>> getWorks() {
        return dfe -> {
            Collection<Work.Status> statuses = arg(dfe, "status", new TypeReference<List<Work.Status>>() {});
            if (statuses==null) {
                return workRepo.findAll();
            }
            return workRepo.findAllByStatusIn(statuses);
        };
    }

    public DataFetcher<Work> getWork() {
        return dfe -> workRepo.getByWorkNumber(dfe.getArgument("workNumber"));
    }

    public DataFetcher<Iterable<Work>> getWorksCreatedBy() {
        return dfe -> {
            String username = dfe.getArgument("username");
            User user = userRepo.getByUsername(username);
            return workService.getWorksCreatedBy(user);
        };
    }

    public DataFetcher<List<WorkWithComment>> getWorksWithComments() {
        return dfe -> {
            Collection<Work.Status> statuses = arg(dfe, "status", new TypeReference<List<Work.Status>>() {});
            return workService.getWorksWithComments(statuses);
        };
    }

    public DataFetcher<SuggestedWorkResponse> getSuggestedWorkForLabwareBarcodes() {
        return dfe -> {
            List<String> barcodes = dfe.getArgument("barcodes");
            boolean includeInactive = argOrFalse(dfe, "includeInactive");
            return workService.suggestWorkForLabwareBarcodes(barcodes, includeInactive);
        };
    }

    public DataFetcher<List<Labware>> getSuggestedLabwareForWork() {
        return dfe -> {
            String workNumber = dfe.getArgument("workNumber");
            boolean forRelease = argOrFalse(dfe, "forRelease");
            return workService.suggestLabwareForWorkNumber(workNumber, forRelease);
        };
    }

    public DataFetcher<List<FlagDetail>> getFlagDetails() {
        return dfe -> {
            List<String> barcodes = dfe.getArgument("barcodes");
            List<Labware> labware = labwareRepo.findByBarcodeIn(barcodes);
            return flagLookupService.lookUpDetails(labware);
        };
    }

    public DataFetcher<List<AddressString>> getMeasurementValueFromLabwareOrParent() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            String name = dfe.getArgument("name");
            var map = measurementService.getMeasurementsFromLabwareOrParent(barcode, name);
            return measurementService.toAddressStrings(map);
        };
    }

    public DataFetcher<List<LabwareRoi>> labwareRois() {
        return dfe -> {
            List<String> barcodes = dfe.getArgument("barcodes");
            return roiService.labwareRois(barcodes);
        };
    }

    public DataFetcher<List<RoiResult>> labwareRunRois() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            String run = dfe.getArgument("run");
            return roiService.labwareRunRois(barcode, run);
        };
    }

    public DataFetcher<Operation> findLatestOperation() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            String opName = dfe.getArgument("operationType");
            return recentOpService.findLatestOp(barcode, opName);
        };
    }

    public DataFetcher<List<Address>> cleanedOutAddresses() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            return cleanedOutSlotService.findCleanedOutAddresses(barcode);
        };
    }

    public DataFetcher<List<StanFile>> listStanFiles() {
        return dfe -> {
            List<String> workNumbers = dfe.getArgument("workNumbers");
            return fileStoreService.list(workNumbers);
        };
    }

    public DataFetcher<Iterable<User>> getUsers() {
        return dfe -> {
            boolean includeDisabled = argOrFalse(dfe,"includeDisabled");
            Iterable<User> users = userRepo.findAll();
            if (includeDisabled) {
                return users;
            }
            return BasicUtils.stream(users)
                    .filter(user -> user.getRole() != User.Role.disabled)
                    .collect(toList());
        };
    }

    public DataFetcher<FindResult> find() {
        return dfe -> {
            FindRequest request = arg(dfe, "request", FindRequest.class);
            return findService.find(request);
        };
    }

    public DataFetcher<History> historyForSampleId() {
        return dfe -> {
            int sampleId = dfe.getArgument("sampleId");
            return historyService.getHistoryForSampleId(sampleId);
        };
    }

    public DataFetcher<History> historyForExternalName() {
        return dfe -> {
            String externalName = dfe.getArgument("externalName");
            return historyService.getHistoryForExternalName(externalName);
        };
    }

    private History fetchHistory(DataFetchingEnvironment dfe) {
        String workNumber = dfe.getArgument("workNumber");
        String barcode = dfe.getArgument("barcode");
        List<String> externalNames = dfe.getArgument("externalName");
        List<String> donorNames = dfe.getArgument("donorName");
        String eventType = dfe.getArgument("eventType");
        return historyService.getHistory(workNumber, barcode, externalNames, donorNames, eventType);
    }

    public DataFetcher<History> history() {
        return this::fetchHistory;
    }

    public DataFetcher<GraphSVG> historyGraph() {
        return dfe -> {
            History history = fetchHistory(dfe);
            HistoryGraph graph = graphService.createGraph(history);
            Number zoomNumber = dfe.getArgument("zoom");
            Number fontNumber = dfe.getArgument("fontSize");
            float zoom = (zoomNumber==null ? 1 : zoomNumber.floatValue());
            Integer fontInteger = (fontNumber==null ? null : fontNumber.intValue());
            return graphService.render(graph, zoom, fontInteger);
        };
    }

    public DataFetcher<History> historyForDonorName() {
        return dfe -> {
            String donorName = dfe.getArgument("donorName");
            return historyService.getHistoryForDonorName(donorName);
        };
    }

    public DataFetcher<History> historyForWorkNumber() {
        return dfe -> {
            String workNumber = dfe.getArgument("workNumber");
            return historyService.getHistoryForWorkNumber(workNumber);
        };
    }

    public DataFetcher<History> historyForLabwareBarcode() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            return historyService.getHistoryForLabwareBarcode(barcode);
        };
    }

    public DataFetcher<List<String>> eventTypes() {
        return dfe -> historyService.getEventTypes();
    }

    public DataFetcher<List<WorkProgress>> workProgress() {
        return dfe -> {
            String workNumber = dfe.getArgument("workNumber");
            List<String> workTypeNames = dfe.getArgument("workTypes");
            List<Work.Status> statuses = arg(dfe, "statuses", new TypeReference<>() {});
            List<String> programNames = dfe.getArgument("programs");
            List<String> requesterNames = dfe.getArgument("requesters");
            return workProgressService.getProgress(workNumber, workTypeNames, programNames, statuses, requesterNames);
        };
    }

    public DataFetcher<AnalyserScanData> analyserScanData() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            return analyserScanDataService.load(barcode);
        };
    }

    public DataFetcher<Set<String>> runNames() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            return lwNoteService.findNoteValuesForBarcode(barcode, AnalyserServiceImp.RUN_NAME);
        };
    }

    public DataFetcher<List<?>> labwareBioRiskCodes() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            return labwareService.getSampleBioRisks(barcode);
        };
    }

    public DataFetcher<WorkSummaryData> worksSummary() {
        return dfe -> workSummaryService.loadWorkSummary();
    }
  
    public DataFetcher<PlanData> getPlanData() {
        return dfe -> {
            boolean requestsFlags = requestsField(dfe, "**/flagged");
            return planService.getPlanData(dfe.getArgument("barcode"), requestsFlags);
        };
    }

    public DataFetcher<List<StainType>> getEnabledStainTypes() {
        return dfe -> stainService.getEnabledStainTypes();
    }

    public DataFetcher<List<Comment>> getStainReagentTypes() {
        return dfe -> commentRepo.findAllByCategoryInAndEnabled(StainType.H_AND_E_MEASUREMENTS, true);
    }

    public DataFetcher<VisiumPermData> getVisiumPermData() {
        return dfe -> visiumPermDataService.load(dfe.getArgument("barcode"));
    }

    public DataFetcher<ExtractResult> getExtractResult() {
        return dfe -> {
            boolean loadFlags = requestsField(dfe, "**/flagged");
            return extractResultQueryService.getExtractResult(dfe.getArgument("barcode"), loadFlags);
        };
    }

    public DataFetcher<List<OpPassFail>> getPassFails() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            String opName = dfe.getArgument("operationType");
            return passFailQueryService.getPassFails(barcode, opName);
        };
    }

    public DataFetcher<ReagentPlate> getReagentPlate() {
        return dfe -> {
            String barcode = dfe.getArgument("barcode");
            return reagentPlateRepo.findByBarcode(barcode).orElse(null);
        };
    }

    public DataFetcher<List<NextReplicateData>> nextReplicateNumbers() {
        return dfe -> {
            List<String> barcodes = dfe.getArgument("barcodes");
            return nextReplicateService.getNextReplicateData(barcodes);
        };
    }

    public DataFetcher<List<Operation>> getLabwareOperations() {
        return dfe -> labwareService.getLabwareOperations(dfe.getArgument("barcode"), dfe.getArgument("operationType"));
    }

    /** Gets the slide costing (if any) recorded for the specified labware. */
    public DataFetcher<SlideCosting> getLabwareCosting() {
        return dfe -> labwareService.getLabwareCosting(dfe.getArgument("barcode"));
    }

    public DataFetcher<VersionInfo> versionInfo() {
        return dfe -> versionInfo;
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

    private boolean requestsField(DataFetchingEnvironment dfe, String glob) {
       return dfe.getSelectionSet().contains(glob);
    }

    @FunctionalInterface
    private interface BoolObjFunction<E> {
        E apply(boolean arg);
    }
}
