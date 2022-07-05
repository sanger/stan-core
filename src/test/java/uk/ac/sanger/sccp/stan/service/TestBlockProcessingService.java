package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockLabware;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;

/**
 * Tests {@link BlockProcessingServiceImp}
 */
public class TestBlockProcessingService {
    private LabwareValidatorFactory mockLwValFactory;
    private Validator<String> mockPrebarcodeValidator;
    private Validator<String> mockReplicateValidator;
    private LabwareRepo mockLwRepo;
    private SlotRepo mockSlotRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private OperationCommentRepo mockOpCommentRepo;
    private MediumRepo mockMediumRepo;
    private LabwareTypeRepo mockLtRepo;
    private BioStateRepo mockBsRepo;
    private TissueRepo mockTissueRepo;
    private SampleRepo mockSampleRepo;
    private CommentValidationService mockCommentValidationService;
    private OperationService mockOpService;
    private LabwareService mockLwService;
    private WorkService mockWorkService;

    private BlockProcessingServiceImp service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        mockLwValFactory = mock(LabwareValidatorFactory.class);
        mockPrebarcodeValidator = mock(Validator.class);
        mockReplicateValidator = mock(Validator.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpCommentRepo = mock(OperationCommentRepo.class);
        mockMediumRepo = mock(MediumRepo.class);
        mockLtRepo = mock(LabwareTypeRepo.class);
        mockBsRepo = mock(BioStateRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockCommentValidationService = mock(CommentValidationService.class);
        mockOpService = mock(OperationService.class);
        mockLwService = mock(LabwareService.class);
        mockWorkService = mock(WorkService.class);

        service = spy(new BlockProcessingServiceImp(mockLwValFactory, mockPrebarcodeValidator, mockReplicateValidator,
                mockLwRepo, mockSlotRepo, mockOpTypeRepo, mockOpCommentRepo, mockMediumRepo, mockLtRepo,
                mockBsRepo, mockTissueRepo, mockSampleRepo,
                mockCommentValidationService, mockOpService, mockLwService, mockWorkService));
    }

    @Test
    public void testPerform_noLabware() {
        User user = EntityFactory.getUser();
        TissueBlockRequest request = new TissueBlockRequest(List.of());
        assertValidationException(() -> service.perform(user, request),
                "The request could not be validated.",
                "No labware specified in request.");
        verifyNoInteractions(mockWorkService);
        verify(service, never()).loadSources(any(), any());
        verify(service, never()).loadEntities(any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(service, never()).checkPrebarcodes(any(), any(), any());
        verifyNoCreation();
    }

    @Test
    public void testPerform_invalid() {
        User user = EntityFactory.getUser();
        TissueBlockLabware block = new TissueBlockLabware("STAN-1", "lt", "1a", "med");
        TissueBlockRequest request = new TissueBlockRequest(List.of(block), "SGP5", List.of("STAN-1"));
        UCMap<Labware> sources = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        LabwareType lt = EntityFactory.makeLabwareType(1,1);
        lt.setName("lt");
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, lt);
        Work work = new Work(5, "SGP5", null, null, null, null, null);
        Map<Integer, Comment> commentMap = Map.of(50, new Comment(50, "Interesting", "science"));
        UCMap<Medium> mediums = UCMap.from(Medium::getName, new Medium(100, "med"));

        final String problem = "Bad sources.";

        stubValidation(sources, lwTypes, work, commentMap, mediums, problem);

        assertValidationException(() -> service.perform(user, request),
                "The request could not be validated.", problem);

        verifyValidation(request, sources, lwTypes);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testPerform(boolean simple) {
        Work work = (simple ? null : new Work(5, "SGP5", null, null, null, null, null));
        Comment comment = (simple ? null : new Comment(50, "Interesting", "science"));
        TissueBlockLabware block = new TissueBlockLabware("STAN-1", "lt", "1a", "Med");
        TissueBlockRequest request = new TissueBlockRequest(List.of(block));
        if (!simple) {
            block.setCommentId(comment.getId());
            block.setPreBarcode("PREBC1");
            request.setWorkNumber(work.getWorkNumber());
            request.setDiscardSourceBarcodes(List.of("STAN-1"));
        }
        UCMap<Labware> sources = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        LabwareType lt = EntityFactory.makeLabwareType(1,1);
        lt.setName("lt");
        UCMap<LabwareType> ltMap = UCMap.from(LabwareType::getName, lt);
        Map<Integer, Comment> commentMap = Map.of(50, new Comment(50, "Hello", "stuff"));
        Medium medium = EntityFactory.getMedium();
        UCMap<Medium> mediums = UCMap.from(Medium::getName, medium);

        stubValidation(sources, ltMap, work, commentMap, mediums, null);

        List<Sample> samples = List.of(EntityFactory.getSample());
        List<Labware> dests = List.of(EntityFactory.makeLabware(lt, samples.get(0)));
        List<Operation> ops = List.of(new Operation());
        doReturn(samples).when(service).createSamples(any(), any(), any());
        doReturn(dests).when(service).createDestinations(any(), any(), any());
        doReturn(ops).when(service).createOperations(any(), any(), any(), any(), any());
        doNothing().when(service).discardSources(any(), any());

        User user = EntityFactory.getUser();
        assertEquals(new OperationResult(ops, dests), service.perform(user, request));

        verifyValidation(request, sources, ltMap);
        verifyCreation(request, user, sources, dests, mediums, commentMap, work, samples, ltMap, ops);
    }

    private void stubValidation(UCMap<Labware> sources, UCMap<LabwareType> lwTypes, Work work,
                                Map<Integer, Comment> commentMap, UCMap<Medium> mediums, String problem) {
        if (problem!=null) {
            doAnswer(Matchers.addProblem(problem, sources)).when(service).loadSources(any(), any());
        } else {
            doReturn(sources).when(service).loadSources(any(), any());
        }
        doReturn(work).when(mockWorkService).validateUsableWork(any(), any());
        doReturn(lwTypes).when(service).loadEntities(any(), any(), any(), eq("Labware type"), any(), anyBoolean(), any());
        doReturn(mediums).when(service).loadEntities(any(), any(), any(), eq("Medium"), any(), anyBoolean(), any());
        doNothing().when(service).checkPrebarcodes(any(), any(), any());
        doReturn(commentMap).when(service).loadComments(any(), any());
        doNothing().when(service).checkReplicates(any(), any(), any());
        doNothing().when(service).checkDiscardBarcodes(any(), any());
    }

    private void verifyValidation(TissueBlockRequest request, UCMap<Labware> sources, UCMap<LabwareType> lwTypes) {
        //noinspection unchecked
        ArgumentCaptor<Set<String>> problemsCaptor = ArgumentCaptor.forClass(Set.class);
        Medium medium = EntityFactory.getMedium();
        List<Medium> medList = List.of(medium);
        LabwareType lt = EntityFactory.getTubeType();
        final List<LabwareType> ltList = List.of(lt);
        TissueBlockLabware block = request.getLabware().get(0);

        verify(service).loadSources(problemsCaptor.capture(), same(request));
        final Set<String> problems = problemsCaptor.getValue();
        verify(mockWorkService).validateUsableWork(same(problems), eq(request.getWorkNumber()));
        when(mockLtRepo.findAllByNameIn(any())).thenReturn(ltList);
        verify(service).loadEntities(same(problems), same(request),
                functionLike(TissueBlockLabware::getLabwareType, block), eq("Labware type"),
                functionLike(LabwareType::getName, lt), eq(true),
                functionGiving(null, ltList));
        verify(service).checkPrebarcodes(same(problems), same(request), same(lwTypes));
        verify(service).loadComments(same(problems), same(request));
        when(mockMediumRepo.findAllByNameIn(any())).thenReturn(medList);
        verify(service).loadEntities(same(problems), same(request),
                functionLike(TissueBlockLabware::getMedium, block), eq("Medium"),
                functionLike(Medium::getName, medium), eq(true),
                functionGiving(null, medList));
        verify(service).checkReplicates(same(problems), same(request), same(sources));
        verify(service).checkDiscardBarcodes(same(problems), same(request));
    }

    private void verifyNoCreation() {
        verify(service, never()).createSamples(any(), any(), any());
        verify(service, never()).createDestinations(any(), any(), any());
        verify(service, never()).createOperations(any(), any(), any(), any(), any());
        verify(mockWorkService, never()).link(any(Work.class), any());
        verify(service, never()).discardSources(any(), any());
    }

    private void verifyCreation(TissueBlockRequest request, User user, UCMap<Labware> sources, List<Labware> dests,
                                UCMap<Medium> mediums, Map<Integer, Comment> commentMap, Work work,
                                List<Sample> samples, UCMap<LabwareType> ltMap, List<Operation> ops) {
        verify(service).createSamples(same(request), same(sources), same(mediums));
        verify(service).createDestinations(same(request), same(samples), same(ltMap));
        verify(service).createOperations(same(request), same(user), same(sources), same(dests), same(commentMap));
        if (work!=null) {
            verify(mockWorkService).link(same(work), same(ops));
        } else {
            verify(mockWorkService, never()).link(any(Work.class), any());
        }
        verify(service).discardSources(any(), any());
    }

    @ParameterizedTest
    @MethodSource("loadSourcesArgs")
    public void testLoadSources(Collection<String> barcodes, List<Labware> labware, BioState bs,
                                Collection<String> valErrors,
                                Collection<String> expectedProblems) {
        TissueBlockRequest request = new TissueBlockRequest(
                barcodes.stream()
                        .map(bc -> {
                            TissueBlockLabware block = new TissueBlockLabware();
                            block.setSourceBarcode(bc);
                            return block;
                        })
                        .collect(toList())
        );
        when(mockBsRepo.getByName("Original sample")).thenReturn(bs);
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        when(val.getErrors()).thenReturn(valErrors);
        when(val.loadLabware(any(), any())).thenReturn(labware);
        when(val.getLabware()).thenReturn(labware);

        final List<String> problems = new ArrayList<>(expectedProblems.size());
        UCMap<Labware> lwMap = service.loadSources(problems, request);

        assertThat(lwMap).hasSize(labware.size());
        labware.forEach(lw -> assertSame(lw, lwMap.get(lw.getBarcode())));

        Set<String> nonNullBarcodes = barcodes.stream()
                .filter(bc -> bc!=null && !bc.isEmpty())
                .collect(toSet());

        verify(val).loadLabware(mockLwRepo, nonNullBarcodes);
        verify(val).setSingleSample(true);
        verify(val).validateSources();
        verify(val).validateBioState(bs);

        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> loadSourcesArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeLabware(lt);
        lw1.setBarcode("STAN-1");
        Labware lw2 = EntityFactory.makeLabware(lt);
        lw2.setBarcode("STAN-2");
        BioState bs = EntityFactory.getBioState();

        return Arrays.stream(new Object[][]{
                {List.of("STAN-404", "STAN-405"), List.of(), bs, List.of("Unknown barcodes: STAN-404, STAN-405"),
                        List.of("Unknown barcodes: STAN-404, STAN-405")},
                {List.of("STAN-1", "STAN-2", ""), List.of(lw1, lw2), bs, List.of(),
                        List.of("Source barcode missing.")},
                {Arrays.asList("STAN-404", null, "STAN-1"), List.of(lw1), bs, List.of("Bad barcode: STAN-404"),
                        List.of("Bad barcode: STAN-404", "Source barcode missing.")},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("checkPrebarcodesArgs")
    public void testCheckPrebarcodes(List<String> prebarcodes, List<String> lwTypeNames, UCMap<LabwareType> ltMap,
                                     List<Labware> existingLabware, boolean existingFromExternal,
                                     Collection<String> expectedProblems) {
        when(mockPrebarcodeValidator.validate(any(), any())).then(invocation -> {
            String barcode = invocation.getArgument(0);
            Consumer<String> problemConsumer = invocation.getArgument(1);
            if (barcode!=null && barcode.indexOf('!') < 0) {
                return true;
            }
            problemConsumer.accept("Bad barcode: "+barcode);
            return false;
        });
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(existingFromExternal ? List.of() : existingLabware);
        when(mockLwRepo.findByExternalBarcodeIn(any())).thenReturn(existingFromExternal ? existingLabware : List.of());

        final List<String> problems = new ArrayList<>(expectedProblems.size());
        final Iterator<String> lwTypeNameIter = lwTypeNames.iterator();
        TissueBlockRequest request = new TissueBlockRequest(
                prebarcodes.stream()
                        .map(bc -> {
                            TissueBlockLabware block = new TissueBlockLabware();
                            block.setPreBarcode(bc);
                            block.setLabwareType(lwTypeNameIter.next());
                            return block;
                        })
                        .collect(toList())
        );
        service.checkPrebarcodes(problems, request, ltMap);

        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        Set<String> prebarcodeSet = prebarcodes.stream()
                .filter(bc -> bc!=null && !bc.isEmpty())
                .peek(bc -> verify(mockPrebarcodeValidator).validate(eq(bc), any()))
                .map(String::toUpperCase)
                .collect(toSet());
        if (!prebarcodeSet.isEmpty()) {
            verify(mockLwRepo).findByBarcodeIn(prebarcodeSet);
            if (existingFromExternal) {
                verify(mockLwRepo).findByExternalBarcodeIn(prebarcodeSet);
            }
        }
    }

    static Stream<Arguments> checkPrebarcodesArgs() {
        LabwareType pretube = EntityFactory.makeLabwareType(1,1);
        pretube.setName("Pretube");
        pretube.setPrebarcoded(true);
        UCMap<LabwareType> ltMap = UCMap.from(LabwareType::getName, EntityFactory.getTubeType(), pretube);
        Labware existingLw = EntityFactory.makeEmptyLabware(pretube);
        existingLw.setBarcode("EXISTING");
        existingLw.setExternalBarcode("EXISTING");
        List<Labware> existingLwList = List.of(existingLw);
        return Arrays.stream(new Object[][]{
                {Arrays.asList("ALPHA", null, "BETA", null), List.of("Pretube", "Tube", "Pretube", "Tube"),
                        ltMap, List.of(), false, List.of()},
                {Arrays.asList("ALPHA", "BETA"), List.of("Tube", "tube"), ltMap, List.of(), false,
                        List.of("A barcode is not expected for labware type [Tube].")},
                {Arrays.asList(null, null), List.of("pretube", "pretube"), ltMap, List.of(), false,
                        List.of("A barcode is required for labware type [Pretube].")},
                {Arrays.asList(null, "ALPHA"), List.of("", "foo"), ltMap, List.of(), false, List.of()},
                {List.of("ALPHA", "Beta", "Alpha"), List.of("Pretube", "Pretube", "Pretube"), ltMap, List.of(), false,
                        List.of("Barcode specified multiple times: [ALPHA]")},
                {Arrays.asList("Existing", "Alpha", null), List.of("Pretube", "Pretube", "Tube"), ltMap,
                        existingLwList, false, List.of("Barcode already in use: [EXISTING]")},
                {Arrays.asList("Existing", "Alpha", null), List.of("Pretube", "Pretube", "Tube"), ltMap,
                        existingLwList, true, List.of("External barcode already in use: [EXISTING]")},
                {Arrays.asList(null, "Alpha", "Beta!"), List.of("Tube", "Pretube", "Pretube"), ltMap,
                        List.of(), false, List.of("Bad barcode: Beta!")},
                {List.of("", "Alpha", "ALPHA", "gamma", "Beta!", "existing"),
                        List.of("Pretube", "Pretube", "Pretube", "Tube", "Pretube", "Pretube"),
                        ltMap, existingLwList, false,
                        List.of("A barcode is not expected for labware type [Tube].",
                                "A barcode is required for labware type [Pretube].",
                                "Barcode specified multiple times: [ALPHA]",
                                "Bad barcode: Beta!",
                                "Barcode already in use: [EXISTING]")},

        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("loadCommentsArgs")
    public void testLoadComments(List<Integer> ids, List<Comment> comments, List<String> errors) {
        final List<Integer> receivedCommentIds = new ArrayList<>(ids.size());
        when(mockCommentValidationService.validateCommentIds(any(), any()))
                .then(invocation -> {
                    Stream<Integer> commentIds = invocation.getArgument(1);
                    commentIds.forEach(receivedCommentIds::add);
                    if (!errors.isEmpty()) {
                        Collection<String> problems = invocation.getArgument(0);
                        problems.addAll(errors);
                    }
                    return comments;
                });
        List<String> problems = new ArrayList<>(errors.size());
        TissueBlockRequest request = new TissueBlockRequest(
                ids.stream()
                        .map(id -> {
                            TissueBlockLabware block = new TissueBlockLabware();
                            block.setCommentId(id);
                            return block;
                        })
                        .collect(toList())
        );
        Map<Integer, Comment> map = service.loadComments(problems, request);
        assertThat(map.values()).containsExactlyInAnyOrderElementsOf(comments);
        for (Comment comment : comments) {
            assertSame(comment, map.get(comment.getId()));
        }
        assertThat(problems).containsExactlyElementsOf(errors);
        verify(mockCommentValidationService).validateCommentIds(any(), any());
        List<Integer> expectedCommentIds = ids.stream()
                .filter(Objects::nonNull)
                .collect(toList());
        assertThat(receivedCommentIds).containsExactlyInAnyOrderElementsOf(expectedCommentIds);
    }

    static Stream<Arguments> loadCommentsArgs() {
        Comment com1 = new Comment(1, "Banana", "Fruit");
        Comment com2 = new Comment(2, "Whale", "Insect");
        List<Comment> comments = List.of(com1, com2);
        return Arrays.stream(new Object[][] {
                {List.of(), List.of(), List.of()},
                {Arrays.asList(null, null), List.of(), List.of()},
                {Arrays.asList(null, 1, 2, 1, null), comments, List.of()},
                {Arrays.asList(null, 1, 2, 1, 5), comments, List.of("Bad comment id: 5")},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("loadEntitiesArgs")
    public <T> void testLoadEntities(TissueBlockRequest request, Function<TissueBlockLabware, String> fieldGetter,
                                     String fieldName, Function<T, String> entityFieldGetter, boolean required,
                                     Collection<T> entities, Collection<String> expectedProblems) {
        final Set<String> lookedUp = new HashSet<>();
        Function<Collection<String>, Collection<T>> lookupFunction = strings -> {
            lookedUp.addAll(strings);
            return entities;
        };
        final List<String> problems = new ArrayList<>(expectedProblems.size());
        var result = service.loadEntities(problems, request, fieldGetter, fieldName, entityFieldGetter,
                required, lookupFunction);
        assertThat(result.values()).containsExactlyInAnyOrderElementsOf(entities);
        for (var entity : entities) {
            assertSame(entity, result.get(entityFieldGetter.apply(entity)));
        }
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        Set<String> expectedFieldValues = request.getLabware().stream()
                .map(fieldGetter)
                .filter(s -> s!=null && !s.isEmpty())
                .collect(toSet());
        assertEquals(lookedUp, expectedFieldValues);
    }

    static Stream<Arguments> loadEntitiesArgs() {
        LabwareType lt1 = EntityFactory.makeLabwareType(1,1);
        lt1.setName("lt1");
        LabwareType lt2 = EntityFactory.makeLabwareType(1,1);
        lt2.setName("lt2");
        List<LabwareType> lwTypes = List.of(lt1, lt2);
        Function<TissueBlockLabware, String> fieldGetter = TissueBlockLabware::getLabwareType;
        Function<LabwareType, String> entityFieldGetter = LabwareType::getName;
        final String fieldName = "Labware type";
        return Arrays.stream(new Object[][]{
                {lwTypesToRequest("lt1", "lt2", "LT1"), fieldGetter, fieldName, entityFieldGetter,
                        true, lwTypes, List.of()},
                {lwTypesToRequest("lt1", "lt2", "", null), fieldGetter, fieldName, entityFieldGetter,
                        false, lwTypes, List.of()},
                {lwTypesToRequest("lt1", "lt2", ""), fieldGetter, fieldName, entityFieldGetter,
                        true, lwTypes, List.of("Labware type missing.")},
                {lwTypesToRequest(""), fieldGetter, fieldName, entityFieldGetter,
                        false, List.of(), List.of()},
                {lwTypesToRequest("lt1", "lt2", null), fieldGetter, fieldName, entityFieldGetter,
                        true, lwTypes, List.of("Labware type missing.")},
                {lwTypesToRequest("LT1", "LT4", "LT5", "lt5"), fieldGetter, fieldName, entityFieldGetter,
                        true, List.of(lt1), List.of("Labware type unknown: [\"LT4\", \"LT5\"]")},
                {lwTypesToRequest("LT1", "LT4", null), fieldGetter, fieldName, entityFieldGetter,
                        true, List.of(lt1), List.of("Labware type unknown: [\"LT4\"]", "Labware type missing.")},
        }).map(Arguments::of);
    }

    private static TissueBlockRequest lwTypesToRequest(String... labwareTypeNames) {
        return new TissueBlockRequest(Arrays.stream(labwareTypeNames)
                .map(ltName -> {
                    TissueBlockLabware block = new TissueBlockLabware();
                    block.setLabwareType(ltName);
                    return block;
                }).collect(toList()));
    }

    @ParameterizedTest
    @MethodSource("checkReplicatesArgs")
    public void testCheckReplicates(TissueBlockRequest request, UCMap<Labware> sources,
                                    Collection<Tissue> existingTissues, Collection<String> expectedProblems) {
        when(mockReplicateValidator.validate(any(), any())).then(invocation -> {
            String string = invocation.getArgument(0);
            if (string.indexOf('!') < 0) {
                return true;
            }
            Consumer<String> addProblem = invocation.getArgument(1);
            addProblem.accept("Bad rep: "+string);
            return false;
        });
        when(mockTissueRepo.findByDonorIdAndSpatialLocationIdAndReplicate(anyInt(), anyInt(), any())).then(invocation -> {
            int donorId = invocation.getArgument(0);
            int slId = invocation.getArgument(1);
            String rep = invocation.getArgument(2);
            return existingTissues.stream()
                    .filter(t ->  t.getDonor().getId()==donorId && t.getSpatialLocation().getId()==slId && rep.equalsIgnoreCase(t.getReplicate()))
                    .collect(toList());
        });
        final List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkReplicates(problems, request, sources);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkReplicatesArgs() {
        Donor d1 = new Donor(1, "DONOR1", null, null);
        Donor d2 = new Donor(2, "DONOR2", null, null);
        TissueType tt = new TissueType(1, "Arm", "ARM");
        SpatialLocation sl1 = new SpatialLocation(101, "SL1", 1, tt);
        SpatialLocation sl2 = new SpatialLocation(102, "SL2", 2, tt);
        Tissue t1A = makeTissue(1, "t1A", null, sl1, d1);
        Tissue t1B = makeTissue(2, "t1B", "1b", sl1, d1);
        Tissue t1C = makeTissue(3, "t1C", "1c", sl2, d1);
        Tissue t2A = makeTissue(4, "t2A", null, sl1, d2);
        Tissue t2B = makeTissue(5, "t2B", "2b", sl2, d2);
        final List<Tissue> tissues = List.of(t1A, t1B, t1C, t2A, t2B);
        // Cases:
        LabwareType lt = EntityFactory.getTubeType();
        BioState bs = EntityFactory.getBioState();
        Labware lw1 = EntityFactory.makeLabware(lt, new Sample(1, null, t1A, bs));
        lw1.setBarcode("STAN-1");
        Labware lw2 = EntityFactory.makeLabware(lt, new Sample(2, null, t2A, bs));
        lw2.setBarcode("STAN-2");
        UCMap<Labware> sources = UCMap.from(Labware::getBarcode, lw1, lw2);

        return Arrays.stream(new Object[][] {
                {requestForReplicates("STAN-1", "5", "STAN-1", "6", "STAN-1", "1c")},
                {requestForReplicates("STAN-1", "5", "STAN-1", "5", "STAN-2", "6", "Stan-2", "6", "STAN-2", "7"),
                "Same replicate specified multiple times: [{Donor: DONOR1, Tissue type: Arm, Spatial location: 1, Replicate: 5}" +
                        ", {Donor: DONOR2, Tissue type: Arm, Spatial location: 1, Replicate: 6}]"},
                {requestForReplicates("STAN-1", null), "Missing replicate for some blocks."},
                {requestForReplicates("STAN-1", ""), "Missing replicate for some blocks."},
                {requestForReplicates("STAN-1", "5", "Stan-1", "1b"), "Replicate already exists in the database: " +
                        "[{Donor: DONOR1, Tissue type: Arm, Spatial location: 1, Replicate: 1b}]"},
                {requestForReplicates(null, "1b", "STAN-2", "5F", "Stan-2", "5f", "Stan-1", null,
                        "STAN-1", "1b"),
                List.of("Same replicate specified multiple times: [{Donor: DONOR2, Tissue type: Arm, Spatial location: 1, Replicate: 5f}]",
                        "Missing replicate for some blocks.",
                        "Replicate already exists in the database: [{Donor: DONOR1, Tissue type: Arm, Spatial location: 1, Replicate: 1b}]")},
        }).map(arr -> {
            List<?> problems;
            if (arr.length < 2) {
                problems = List.of();
            } else if (arr[1] instanceof String) {
                problems = List.of(arr[1]);
            } else {
                problems = (List<?>) arr[1];
            }
            return Arguments.of(arr[0], sources, tissues, problems);
        });
    }

    static Tissue makeTissue(Integer id, String name, String replicate, SpatialLocation sl, Donor d) {
        return new Tissue(id, name, replicate, sl, d, null, null, null, null, null);
    }

    static TissueBlockRequest requestForReplicates(String... barcodesAndReplicates) {
        assert barcodesAndReplicates.length % 2 == 0;
        List<TissueBlockLabware> blocks = new ArrayList<>(barcodesAndReplicates.length/2);
        for (int i = 0; i < barcodesAndReplicates.length; i += 2) {
            TissueBlockLabware block = new TissueBlockLabware();
            block.setSourceBarcode(barcodesAndReplicates[i]);
            block.setReplicate(barcodesAndReplicates[i+1]);
            blocks.add(block);
        }
        return new TissueBlockRequest(blocks);
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";;",
            "; Stan-1/Stan-2;",
            "STAN-1; Stan-1/Stan-2/STAN-2;",
            "/Stan-1;Stan-1;A null or empty string was supplied as a barcode to discard.",
            "Stan-1/Stan-4/Stan-5; STAN-1/STAN-1/STAN-2; The given list of barcodes to discard includes barcodes " +
                    "that are not specified as source barcodes in this request: [\"Stan-4\", \"Stan-5\"]",
            "Stan-1//Stan-5; STAN-1/STAN-2; The given list of barcodes to discard includes a barcode that is not " +
                    "specified as a source barcode in this request: [\"Stan-5\"]/A null or empty string was " +
                    "supplied as a barcode to discard.",
    }, delimiter=';')
    public void testCheckDiscardBarcodes(String discardBarcodesJoined, String sourceBarcodesJoined, String expectedProblemsJoined) {
        String[] discardBarcodes = (discardBarcodesJoined==null ? new String[0] : discardBarcodesJoined.split("/"));
        String[] sourceBarcodes = (sourceBarcodesJoined==null ? new String[0] : sourceBarcodesJoined.split("/"));
        String[] expectedProblems = (expectedProblemsJoined==null ? new String[0] : expectedProblemsJoined.split("/"));

        List<TissueBlockLabware> blocks = Arrays.stream(sourceBarcodes)
                .map(bc -> {
                    TissueBlockLabware block = new TissueBlockLabware();
                    block.setSourceBarcode(bc);
                    return block;
                }).collect(toList());
        TissueBlockRequest request = new TissueBlockRequest(blocks, null, Arrays.asList(discardBarcodes));

        final List<String> problems = new ArrayList<>(expectedProblems.length);
        service.checkDiscardBarcodes(problems, request);
        assertThat(problems).containsExactlyInAnyOrder(expectedProblems);
    }

    @Test
    public void testCreateSamples() {
        BioState bs = EntityFactory.getBioState();
        when(mockBsRepo.getByName("Tissue")).thenReturn(bs);
        Medium med1 = new Medium(1, "med1");
        Medium med2 = new Medium(2, "med2");
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        lw1.setBarcode("STAN-1");
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        lw2.setBarcode("STAN-2");

        TissueBlockLabware block1 = new TissueBlockLabware("STAN-1", "lt", "1a", "med1");
        TissueBlockLabware block2 = new TissueBlockLabware("STAN-2", "lt", "2b", "med2");
        TissueBlockRequest request = new TissueBlockRequest(List.of(block1, block2));

        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(10, 20, sam1.getTissue(), bs);

        doReturn(sam1, sam2).when(service).createSample(any(), any(), any(), any());

        List<Sample> samples = service.createSamples(request,
                UCMap.from(Labware::getBarcode, lw1, lw2), UCMap.from(Medium::getName, med1, med2));

        assertThat(samples).containsExactly(sam1, sam2);
        verify(service, times(2)).createSample(any(), any(), any(), any());
        verify(service).createSample(block1, lw1, bs, med1);
        verify(service).createSample(block2, lw2, bs, med2);
    }

    @Test
    public void testCreateSample() {
        Medium med = EntityFactory.getMedium();
        Labware lw = EntityFactory.getTube();
        BioState bs = EntityFactory.getBioState();
        when(mockTissueRepo.save(any())).then(invocation -> {
            Tissue tissue = invocation.getArgument(0);
            assertNull(tissue.getId());
            tissue.setId(500);
            return tissue;
        });
        when(mockSampleRepo.save(any())).then(invocation -> {
            Sample sample = invocation.getArgument(0);
            assertNull(sample.getId());
            sample.setId(600);
            return sample;
        });

        TissueBlockLabware block = new TissueBlockLabware();
        block.setReplicate("2C");

        Sample sample = service.createSample(block, lw, bs, med);
        Tissue tissue = sample.getTissue();
        verify(mockTissueRepo).save(tissue);
        verify(mockSampleRepo).save(sample);
        assertEquals(500, tissue.getId());
        assertEquals(600, sample.getId());
        Tissue original = lw.getFirstSlot().getSamples().get(0).getTissue();
        assertEquals(new Tissue(500, tissue.getExternalName(), "2c", original.getSpatialLocation(), original.getDonor(),
                med, original.getFixative(), original.getHmdmc(), original.getCollectionDate(),
                original.getId()), tissue);
        assertEquals(new Sample(600, null, tissue, bs), sample);
    }

    @Test
    public void testCreateDestinations() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, null, sam1.getTissue(), sam1.getBioState());
        LabwareType lt1 = EntityFactory.makeLabwareType(1,1);
        lt1.setName("lt1");
        LabwareType lt2 = EntityFactory.makeLabwareType(1,1);
        lt2.setName("lt2");
        Labware lw1 = EntityFactory.makeLabware(lt1, sam1);
        Labware lw2 = EntityFactory.makeLabware(lt2, sam2);
        doReturn(lw1, lw2).when(service).createDestination(any(), any(), any());
        TissueBlockLabware block1 = new TissueBlockLabware();
        block1.setLabwareType("lt1");
        block1.setPreBarcode("ABC123");
        TissueBlockLabware block2 = new TissueBlockLabware();
        block2.setLabwareType("lt2");

        TissueBlockRequest request = new TissueBlockRequest(List.of(block1, block2));

        List<Labware> labware = service.createDestinations(request, List.of(sam1, sam2), UCMap.from(LabwareType::getName, lt1, lt2));

        assertThat(labware).containsExactly(lw1, lw2);
        verify(service, times(2)).createDestination(any(), any(), any());
        verify(service).createDestination(lt1, sam1, "ABC123");
        verify(service).createDestination(lt2, sam2, null);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCreateDestination(boolean prebarcoded) {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        String prebarcode = prebarcoded ? "ABC123" : null;
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        when(mockLwService.create(any(), any(), any())).thenReturn(lw);

        assertSame(lw, service.createDestination(lt, sample, prebarcode));
        assertThat(lw.getFirstSlot().getSamples()).containsExactly(sample);
        verify(mockLwService).create(lt, prebarcode, prebarcode);
        verify(mockSlotRepo).save(lw.getFirstSlot());
    }

    @Test
    public void testCreateOperations() {
        Operation op1 = new Operation();
        op1.setId(101);
        Operation op2 = new Operation();
        op2.setId(102);
        OperationType opType = EntityFactory.makeOperationType("Block processing", null);
        when(mockOpTypeRepo.getByName("Block processing")).thenReturn(opType);
        User user = EntityFactory.getUser();
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = IntStream.range(1, 5)
                .mapToObj(i -> {
                    Labware lw = EntityFactory.makeEmptyLabware(lt);
                    lw.setId(i);
                    lw.setBarcode("STAN-"+i);
                    return lw;
                }).toArray(Labware[]::new);
        Comment comment = new Comment(5, "Nice one", "sarcasm");
        Map<Integer, Comment> commentMap = new HashMap<>(1);
        commentMap.put(comment.getId(), comment);

        TissueBlockLabware block1 = new TissueBlockLabware();
        block1.setSourceBarcode("STAN-1");
        block1.setCommentId(comment.getId());
        TissueBlockLabware block2 = new TissueBlockLabware();
        block2.setSourceBarcode("STAN-2");
        TissueBlockRequest request = new TissueBlockRequest(List.of(block1, block2));

        doReturn(op1, op2).when(service).createOperation(any(), any(), any(), any(), any());

        assertThat(service.createOperations(request, user, UCMap.from(Labware::getBarcode, labware[0], labware[1]),
                List.of(labware[2], labware[3]), commentMap)).containsExactly(op1, op2);

        verify(service, times(2)).createOperation(any(), any(), any(), any(), any());
        verify(service).createOperation(opType, user, labware[0], labware[2], comment);
        verify(service).createOperation(opType, user, labware[1], labware[3], null);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCreateOperation(boolean hasComment) {
        Comment comment = (hasComment ? new Comment(5, "Yeah right", "sarcasm") : null);
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Block processing", null);

        final Sample sam0 = EntityFactory.getSample();
        final Sample sam1 = new Sample(sam0.getId()+1, null, sam0.getTissue(), sam0.getBioState());
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam0);
        Labware lw1 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam1);

        Operation op = new Operation();
        op.setId(500);
        when(mockOpService.createOperation(any(), any(), any(), any())).thenReturn(op);

        assertSame(op, service.createOperation(opType, user, lw0, lw1, comment));
        verify(mockOpService).createOperation(opType, user, List.of(
                new Action(null, null, lw0.getFirstSlot(), lw1.getFirstSlot(), sam1, sam0)),
                null);
        if (comment==null) {
            verifyNoInteractions(mockOpCommentRepo);
        } else {
            verify(mockOpCommentRepo).save(new OperationComment(null, comment, 500, sam1.getId(), lw1.getFirstSlot().getId(), null));
        }
    }

    @Test
    public void testDiscardSources_none() {
        service.discardSources(List.of(), new UCMap<>());
    }

    @Test
    public void testDiscardSources() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        Labware lw3 = EntityFactory.makeEmptyLabware(lt);
        service.discardSources(List.of(lw1.getBarcode(), lw2.getBarcode()), UCMap.from(Labware::getBarcode, lw1, lw2, lw3));
        verify(mockLwRepo).saveAll(List.of(lw1, lw2));
        assertTrue(lw1.isDiscarded());
        assertTrue(lw2.isDiscarded());
        assertFalse(lw3.isDiscarded());
    }
}