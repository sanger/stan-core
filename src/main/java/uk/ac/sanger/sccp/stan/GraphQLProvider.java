package uk.ac.sanger.sccp.stan;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.model.User;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * @author dr6
 */
@Component
public class GraphQLProvider {

    private GraphQL graphQL;

    final Transactor transactor;
    final GraphQLDataFetchers graphQLDataFetchers;
    final GraphQLMutation graphQLMutation;
    final GraphQLStore graphQLStore;

    @Autowired
    public GraphQLProvider(Transactor transactor,
                           GraphQLDataFetchers graphQLDataFetchers, GraphQLMutation graphQLMutation, GraphQLStore graphQLStore) {
        this.transactor = transactor;
        this.graphQLDataFetchers = graphQLDataFetchers;
        this.graphQLMutation = graphQLMutation;
        this.graphQLStore = graphQLStore;
    }

    @Bean
    public GraphQL graphQL() {
        return graphQL;
    }

    @PostConstruct
    public void init() throws IOException {
        URL url = Resources.getResource("schema.graphqls");
        String sdl = Resources.toString(url, Charsets.UTF_8);
        GraphQLSchema graphQLSchema = buildSchema(sdl);
        this.graphQL = GraphQL.newGraphQL(graphQLSchema)
                .mutationExecutionStrategy(new AsyncSerialExecutionStrategy(new StanExceptionHandler()))
                .queryExecutionStrategy(new AsyncExecutionStrategy(new StanExceptionHandler()))
                .build();
    }

    private GraphQLSchema buildSchema(String sdl) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("user", graphQLDataFetchers.getUser())
                        .dataFetcher("tissueTypes", graphQLDataFetchers.getTissueTypes())
                        .dataFetcher("hmdmcs", graphQLDataFetchers.getHmdmcs())
                        .dataFetcher("labwareTypes", graphQLDataFetchers.getLabwareTypes())
                        .dataFetcher("mediums", graphQLDataFetchers.getMediums())
                        .dataFetcher("fixatives", graphQLDataFetchers.getFixatives())
                        .dataFetcher("species", graphQLDataFetchers.getSpecies())
                        .dataFetcher("labware", graphQLDataFetchers.findLabwareByBarcode())
                        .dataFetcher("labwareFlagged", graphQLDataFetchers.findLabwareFlagged())
                        .dataFetcher("printers", graphQLDataFetchers.findPrinters())
                        .dataFetcher("comments", graphQLDataFetchers.getComments())
                        .dataFetcher("equipments", graphQLDataFetchers.getEquipments())
                        .dataFetcher("releaseDestinations", graphQLDataFetchers.getReleaseDestinations())
                        .dataFetcher("releaseRecipients", graphQLDataFetchers.getReleaseRecipients())
                        .dataFetcher("releaseColumnOptions", graphQLDataFetchers.getReleaseColumnOptions())
                        .dataFetcher("find", graphQLDataFetchers.find())
                        .dataFetcher("destructionReasons", graphQLDataFetchers.getDestructionReasons())
                        .dataFetcher("projects", graphQLDataFetchers.getProjects())
                        .dataFetcher("programs", graphQLDataFetchers.getPrograms())
                        .dataFetcher("costCodes", graphQLDataFetchers.getCostCodes())
                        .dataFetcher("dnapStudies", graphQLDataFetchers.getDnapStudies())
                        .dataFetcher("dnapStudy", graphQLDataFetchers.getDnapStudy())
                        .dataFetcher("solutions", graphQLDataFetchers.getSolutions())
                        .dataFetcher("omeroProjects", graphQLDataFetchers.getOmeroProjects())
                        .dataFetcher("slotRegions", graphQLDataFetchers.getSlotRegions())
                        .dataFetcher("probePanels", graphQLDataFetchers.getProbePanels())
                        .dataFetcher("samplePositions", graphQLDataFetchers.getSamplePositions())
                        .dataFetcher("workTypes", graphQLDataFetchers.getWorkTypes())
                        .dataFetcher("works", graphQLDataFetchers.getWorks())
                        .dataFetcher("work", graphQLDataFetchers.getWork())
                        .dataFetcher("worksCreatedBy", graphQLDataFetchers.getWorksCreatedBy())
                        .dataFetcher("worksWithComments", graphQLDataFetchers.getWorksWithComments())
                        .dataFetcher("worksSummary", graphQLDataFetchers.worksSummary())
                        .dataFetcher("listFiles", graphQLDataFetchers.listStanFiles())
                        .dataFetcher("stainTypes", graphQLDataFetchers.getEnabledStainTypes())
                        .dataFetcher("stainReagentTypes", graphQLDataFetchers.getStainReagentTypes())
                        .dataFetcher("visiumPermData", graphQLDataFetchers.getVisiumPermData())
                        .dataFetcher("extractResult", graphQLDataFetchers.getExtractResult())
                        .dataFetcher("passFails", graphQLDataFetchers.getPassFails())
                        .dataFetcher("reagentPlate", graphQLDataFetchers.getReagentPlate())
                        .dataFetcher("nextReplicateNumbers", graphQLDataFetchers.nextReplicateNumbers())
                        .dataFetcher("labwareOperations", graphQLDataFetchers.getLabwareOperations())
                        .dataFetcher("labwareCosting", graphQLDataFetchers.getLabwareCosting())
                        .dataFetcher("suggestedWorkForLabware", graphQLDataFetchers.getSuggestedWorkForLabwareBarcodes())
                        .dataFetcher("suggestedLabwareForWork", graphQLDataFetchers.getSuggestedLabwareForWork())
                        .dataFetcher("findLatestOp", graphQLDataFetchers.findLatestOperation())
                        .dataFetcher("labwareFlagDetails", graphQLDataFetchers.getFlagDetails())
                        .dataFetcher("measurementValueFromLabwareOrParent", graphQLDataFetchers.getMeasurementValueFromLabwareOrParent())

                        .dataFetcher("users", graphQLDataFetchers.getUsers())
                        .dataFetcher("planData", graphQLDataFetchers.getPlanData())

                        .dataFetcher("historyForSampleId", graphQLDataFetchers.historyForSampleId())
                        .dataFetcher("historyForExternalName", graphQLDataFetchers.historyForExternalName())
                        .dataFetcher("historyForDonorName", graphQLDataFetchers.historyForDonorName())
                        .dataFetcher("historyForLabwareBarcode", graphQLDataFetchers.historyForLabwareBarcode())
                        .dataFetcher("historyForWorkNumber", graphQLDataFetchers.historyForWorkNumber())
                        .dataFetcher("history", graphQLDataFetchers.history())
                        .dataFetcher("historyGraph", graphQLDataFetchers.historyGraph())
                        .dataFetcher("eventTypes", graphQLDataFetchers.eventTypes())
                        .dataFetcher("workProgress", graphQLDataFetchers.workProgress())

                        .dataFetcher("location", graphQLStore.getLocation())
                        .dataFetcher("stored", graphQLStore.getStored())
                        .dataFetcher("labwareInLocation", graphQLStore.getLabwareInLocation())
                        .dataFetcher("storagePath", graphQLStore.getLocationHierarchy())

                        .dataFetcher("version", graphQLDataFetchers.versionInfo())
                )
                .type(newTypeWiring("Mutation")
                        .dataFetcher("registerAsEndUser", graphQLMutation.userSelfRegister(User.Role.enduser)) // internal transaction
                        .dataFetcher("login", graphQLMutation.logIn())
                        .dataFetcher("logout", graphQLMutation.logOut())
                        .dataFetcher("register", transact(graphQLMutation.register()))
                        .dataFetcher("plan", transact(graphQLMutation.recordPlan()))
                        .dataFetcher("printLabware", graphQLMutation.printLabware()) // not transacted
                        .dataFetcher("confirmOperation", transact(graphQLMutation.confirmOperation()))
                        .dataFetcher("confirmSection", transact(graphQLMutation.confirmSection()))
                        .dataFetcher("release", graphQLMutation.release()) // transaction handled in service
                        .dataFetcher("extract", graphQLMutation.extract()) // transaction handled in service
                        .dataFetcher("destroy", graphQLMutation.destroy()) // transaction handled in service
                        .dataFetcher("registerSections", transact(graphQLMutation.sectionRegister()))
                        .dataFetcher("slotCopy", graphQLMutation.slotCopy()) // transaction handled in service
                        .dataFetcher("recordInPlace", transact(graphQLMutation.recordInPlace()))

                        .dataFetcher("addComment", transact(graphQLMutation.addComment()))
                        .dataFetcher("setCommentEnabled", transact(graphQLMutation.setCommentEnabled()))
                        .dataFetcher("addEquipment", transact(graphQLMutation.addEquipment()))
                        .dataFetcher("setEquipmentEnabled", transact(graphQLMutation.setEquipmentEnabled()))
                        .dataFetcher("renameEquipment", transact(graphQLMutation.renameEquipment()))
                        .dataFetcher("addHmdmc", graphQLMutation.addHmdmc()) // internal transaction
                        .dataFetcher("setHmdmcEnabled", transact(graphQLMutation.setHmdmcEnabled()))
                        .dataFetcher("addDestructionReason", graphQLMutation.addDestructionReason()) // internal transaction
                        .dataFetcher("setDestructionReasonEnabled", transact(graphQLMutation.setDestructionReasonEnabled()))
                        .dataFetcher("addReleaseDestination", graphQLMutation.addReleaseDestination()) // internal transaction
                        .dataFetcher("setReleaseDestinationEnabled", transact(graphQLMutation.setReleaseDestinationEnabled()))
                        .dataFetcher("addReleaseRecipient", transact(graphQLMutation.addReleaseRecipient()))
                        .dataFetcher("updateReleaseRecipientFullName", transact(graphQLMutation.updateReleaseRecipientFullName()))
                        .dataFetcher("setReleaseRecipientEnabled", transact(graphQLMutation.setReleaseRecipientEnabled()))
                        .dataFetcher("addSpecies", graphQLMutation.addSpecies()) // internal transaction
                        .dataFetcher("setSpeciesEnabled", transact(graphQLMutation.setSpeciesEnabled()))
                        .dataFetcher("addProject", graphQLMutation.addProject()) // internal transaction
                        .dataFetcher("setProjectEnabled", transact(graphQLMutation.setProjectEnabled()))
                        .dataFetcher("addProgram", graphQLMutation.addProgram()) // internal transaction
                        .dataFetcher("setProgramEnabled", transact(graphQLMutation.setProgramEnabled()))
                        .dataFetcher("addCostCode", graphQLMutation.addCostCode()) // internal transaction
                        .dataFetcher("setCostCodeEnabled", transact(graphQLMutation.setCostCodeEnabled()))
                        .dataFetcher("addFixative", graphQLMutation.addFixative()) // internal transaction
                        .dataFetcher("setFixativeEnabled", transact(graphQLMutation.setFixativeEnabled()))
                        .dataFetcher("addSolution", graphQLMutation.addSolution()) // internal transaction
                        .dataFetcher("setSolutionEnabled", transact(graphQLMutation.setSolutionEnabled()))
                        .dataFetcher("addOmeroProject", graphQLMutation.addOmeroProject()) // internal transaction
                        .dataFetcher("setOmeroProjectEnabled", transact(graphQLMutation.setOmeroProjectEnabled()))
                        .dataFetcher("addSlotRegion", graphQLMutation.addSlotRegion()) // internal transaction
                        .dataFetcher("setSlotRegionEnabled", transact(graphQLMutation.setSlotRegionEnabled()))
                        .dataFetcher("addProbePanel", graphQLMutation.addProbePanel()) // internal transaction
                        .dataFetcher("setProbePanelEnabled", transact(graphQLMutation.setProbePanelEnabled()))
                        .dataFetcher("addWorkType", graphQLMutation.addWorkType()) // internal transaction
                        .dataFetcher("setWorkTypeEnabled", transact(graphQLMutation.setWorkTypeEnabled()))
                        .dataFetcher("createWork", transact(graphQLMutation.createWork()))
                        .dataFetcher("updateWorkStatus", transact(graphQLMutation.updateWorkStatus()))
                        .dataFetcher("updateWorkNumBlocks", transact(graphQLMutation.updateWorkNumBlocks()))
                        .dataFetcher("updateWorkNumSlides", transact(graphQLMutation.updateWorkNumSlides()))
                        .dataFetcher("updateWorkNumOriginalSamples", transact(graphQLMutation.updateWorkNumOriginalSamples()))
                        .dataFetcher("updateWorkPriority", transact(graphQLMutation.updateWorkPriority()))
                        .dataFetcher("updateWorkOmeroProject", transact(graphQLMutation.updateWorkOmeroProject()))
                        .dataFetcher("updateWorkDnapStudy", transact(graphQLMutation.updateWorkDnapStudy()))
                        .dataFetcher("updateDnapStudies", graphQLMutation.updateDnapStudies()) // transacted internally
                        .dataFetcher("stain", transact(graphQLMutation.stain()))
                        .dataFetcher("unrelease", transact(graphQLMutation.unrelease()))
                        .dataFetcher("recordStainResult", transact(graphQLMutation.recordStainResult()))
                        .dataFetcher("recordExtractResult", transact(graphQLMutation.recordExtractResult()))
                        .dataFetcher("recordPerm", transact(graphQLMutation.recordPerm()))
                        .dataFetcher("visiumAnalysis", transact(graphQLMutation.visiumAnalysis()))
                        .dataFetcher("recordRNAAnalysis", transact(graphQLMutation.recordRNAAnalysis()))
                        .dataFetcher("recordVisiumQC", transact(graphQLMutation.recordVisiumQC()))
                        .dataFetcher("recordOpWithSlotMeasurements", transact(graphQLMutation.recordOpWithSlotMeasurements()))
                        .dataFetcher("recordComplexStain", transact(graphQLMutation.recordComplexStain()))
                        .dataFetcher("aliquot", graphQLMutation.aliquot()) // internal transaction
                        .dataFetcher("reagentTransfer", transact(graphQLMutation.reagentTransfer()))
                        .dataFetcher("registerOriginalSamples", transact(graphQLMutation.registerOriginalSamples()))
                        .dataFetcher("performTissueBlock", graphQLMutation.performTissueBlock()) // internal transaction
                        .dataFetcher("performPotProcessing", graphQLMutation.performPotProcessing()) // internal transaction
                        .dataFetcher("recordSampleProcessingComments", transact(graphQLMutation.addSampleProcessingComments()))
                        .dataFetcher("addExternalID", transact(graphQLMutation.addExternalID()))
                        .dataFetcher("performParaffinProcessing", transact(graphQLMutation.performParaffinProcessing()))
                        .dataFetcher("performSolutionTransfer", transact(graphQLMutation.performSolutionTransfer()))
                        .dataFetcher("recordOpWithSlotComments", transact(graphQLMutation.performOpWithSlotComments()))
                        .dataFetcher("recordProbeOperation", transact(graphQLMutation.recordProbeOperation()))
                        .dataFetcher("recordCompletion", transact(graphQLMutation.recordCompletion()))
                        .dataFetcher("recordAnalyser", transact(graphQLMutation.recordAnalyser()))
                        .dataFetcher("flagLabware", transact(graphQLMutation.flagLabware()))
                        .dataFetcher("recordQCLabware", transact(graphQLMutation.recordQcLabware()))
                        .dataFetcher("recordOrientationQC", transact(graphQLMutation.recordOrientationQC()))
                        .dataFetcher("reactivateLabware", transact(graphQLMutation.reactivateLabware()))
                        .dataFetcher("libraryPrep", graphQLMutation.libraryPrep()) // internal transaction
                        .dataFetcher("segmentation", transact(graphQLMutation.segmentation()))

                        .dataFetcher("addUser", transact(graphQLMutation.addUser()))
                        .dataFetcher("setUserRole", transact(graphQLMutation.setUserRole()))

                        .dataFetcher("store", graphQLStore.store())
                        .dataFetcher("storeBarcode", graphQLStore.storeBarcode())
                        .dataFetcher("unstoreBarcode", graphQLStore.unstoreBarcode())
                        .dataFetcher("empty", graphQLStore.empty())
                        .dataFetcher("transfer", graphQLStore.transfer())
                        .dataFetcher("setLocationCustomName", graphQLStore.setLocationCustomName())
                )
                .scalar(GraphQLCustomTypes.ADDRESS)
                .scalar(GraphQLCustomTypes.TIMESTAMP)
                .scalar(GraphQLCustomTypes.DATE)
                .build();
    }

    private <T> DataFetcher<T> transact(DataFetcher<T> dataFetcher) {
        return transactor.dataFetcher("Mutation transaction", dataFetcher);
    }
}