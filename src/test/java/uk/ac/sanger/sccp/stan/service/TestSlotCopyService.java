package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.*;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link SlotCopyServiceImp}
 * @author dr6
 */
public class TestSlotCopyService {
    @Mock
    OperationTypeRepo mockOpTypeRepo;
    @Mock
    LabwareTypeRepo mockLwTypeRepo;
    @Mock
    LabwareRepo mockLwRepo;
    @Mock
    SampleRepo mockSampleRepo;
    @Mock
    SlotRepo mockSlotRepo;
    @Mock
    BioStateRepo mockBsRepo;
    @Mock
    LabwareNoteRepo mockLwNoteRepo;
    @Mock
    LabwareService mockLwService;
    @Mock
    OperationService mockOpService;
    @Mock
    StoreService mockStoreService;
    @Mock
    WorkService mockWorkService;
    @Mock
    LabwareValidatorFactory mockLabwareValidatorFactory;
    @Mock
    EntityManager mockEntityManager;
    @Mock
    Transactor mockTransactor;
    @Mock
    Validator<String> mockBarcodeValidator;

    private SlotCopyServiceImp service;
    private OperationType opType;
    private BioState tissue, cdna;
    private User user;
    private LabwareType plateType;
    private LabwareType slideType;
    private List<Sample> sourceSamples;
    private List<Labware> sourceLabware;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        tissue = new BioState(1, "Tissue");
        cdna = new BioState(3, "cDNA");
        opType = EntityFactory.makeOperationType("cDNA", cdna, OperationTypeFlag.MARK_SOURCE_USED);
        plateType = EntityFactory.makeLabwareType(8, 12);
        plateType.setName("platetype");
        slideType = EntityFactory.makeLabwareType(4, 1);
        user = EntityFactory.getUser();

        service = spy(new SlotCopyServiceImp(mockOpTypeRepo, mockLwTypeRepo, mockLwRepo, mockSampleRepo, mockSlotRepo,
                mockBsRepo, mockLwNoteRepo,
                mockLwService, mockOpService, mockStoreService, mockWorkService, mockLabwareValidatorFactory, mockEntityManager,
                mockTransactor, mockBarcodeValidator));
    }

    @AfterEach
    void teardown() throws Exception {
        mocking.close();
    }

    private List<Sample> makeSourceSamples(BioState bioState) {
        if (sourceSamples==null) {
            Tissue tissue = EntityFactory.getTissue();
            sourceSamples = IntStream.of(1, 2, 3)
                    .mapToObj(i -> new Sample(10 + i, i, tissue, bioState))
                    .collect(toList());
        }
        return sourceSamples;
    }

    private List<Labware> makeSourceLabware() {
        if (sourceLabware==null) {
            makeSourceSamples(tissue);
            sourceLabware = List.of(
                    EntityFactory.makeLabware(slideType, sourceSamples.get(0), sourceSamples.get(1)),
                    EntityFactory.makeLabware(slideType, sourceSamples.get(1), sourceSamples.get(2))
            );
            sourceLabware.get(0).setBarcode("STAN-001");
            sourceLabware.get(1).setBarcode("STAN-002");
        }
        return sourceLabware;
    }

    private UCMap<Labware> makeLabwareMap() {
        return UCMap.from(makeSourceLabware(), Labware::getBarcode);
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testPerform(boolean valid) {
        SlotCopyRequest request = new SlotCopyRequest("op", "thing", List.of(), null, null, null);
        ValidationException ex = valid ? null : new ValidationException("Bad", List.of());
        Operation op = new Operation(200, opType, null, null, null);
        OperationResult result = valid ? new OperationResult(List.of(op), List.of()) : null;
        Matchers.mockTransactor(mockTransactor);
            doAnswer(invocation -> {
                if (valid) {
                    Set<String> bcs = invocation.getArgument(2);
                    bcs.add("Alpha");
                    bcs.add("Beta");
                    return result;
                }
                throw ex;

            }).when(service).performInsideTransaction(any(), any(), any());
        if (valid) {
            assertSame(result, service.perform(user, request));
        } else {
            assertSame(ex, assertThrows(ValidationException.class, () -> service.perform(user, request)));
        }
        verify(service).performInsideTransaction(same(user), same(request), notNull());
        if (valid) {
            verify(mockStoreService).discardStorage(user, Set.of("Alpha", "Beta"));
        } else {
            verifyNoInteractions(mockStoreService);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testPerformInsideTransaction(boolean valid) {
        List<SlotCopyContent> contents = List.of(new SlotCopyContent("SOURCE1", new Address(1, 2), new Address(3, 4)));
        Work work = new Work(50, "SGP5000", null, null, null, null, Work.Status.active);
        when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        SlotCopyRequest request = new SlotCopyRequest(opType.getName(), plateType.getName(), contents, work.getWorkNumber(), "pbc", null);
        when(mockOpTypeRepo.findByName(any())).thenReturn(Optional.of(opType));
        when(mockLwTypeRepo.findByName(any())).thenReturn(Optional.of(plateType));
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, plateType);
        doReturn(lwTypes).when(service).loadLabwareTypes(any(), any());
        doNothing().when(service).checkPreBarcodes(any(), any(), any());
        UCMap<Labware> lwMap = makeLabwareMap();
        doReturn(lwMap).when(service).loadSources(any(), any());
        doNothing().when(service).validateSources(any(), any());
        UCMap<Labware.State> bcStates = new UCMap<>();
        bcStates.put("Alpha", Labware.State.used);
        bcStates.put("Beta", Labware.State.discarded);
        doReturn(bcStates).when(service).checkListedSources(any(), any());
        doNothing().when(service).checkPreBarcodes(any(), any(), any());
        doNothing().when(service).checkPreBarcodesInUse(any(), any());
        doNothing().when(service).validateOps(any(), any(), any(), any());
        UCMap<BioState> bsMap = UCMap.from(BioState::getName, new BioState(40, "Fried"));
        doReturn(bsMap).when(service).validateBioStates(any(), any());
        OperationResult opResult = new OperationResult(List.of(), List.of());
        doReturn(opResult).when(service).executeOps(any(), any(), any(), any(), any(), any(), any());
        if (valid) {
            doNothing().when(service).validateContents(any(), any(), any(), any());
        } else {
            doAnswer(Matchers.addProblem("Bananas")).when(service).validateContents(any(), any(), any(), any());
        }
        final Set<String> barcodesToUnstore = new HashSet<>();
        if (valid) {
            assertSame(opResult, service.performInsideTransaction(user, request, barcodesToUnstore));
        } else {
            ValidationException ex = assertThrows(ValidationException.class, () -> service.performInsideTransaction(user, request, barcodesToUnstore));
            //noinspection unchecked
            assertThat((Collection<Object>) ex.getProblems()).containsOnly("Bananas");
        }
        verify(mockOpTypeRepo).findByName(request.getOperationType());
        verify(service).loadLabwareTypes(notNull(), same(request.getDestinations()));
        verify(service).checkPreBarcodes(notNull(), same(request.getDestinations()), same(lwTypes));
        verify(service).checkPreBarcodesInUse(notNull(), same(request.getDestinations()));
        verify(service).loadSources(notNull(), same(request));
        verify(service).validateSources(notNull(), eq(lwMap.values()));
        verify(service).checkListedSources(notNull(), same(request));
        verify(service).validateContents(notNull(), same(lwTypes), same(lwMap), same(request));
        verify(service).validateOps(notNull(), same(request.getDestinations()), same(opType), same(lwTypes));

        if (valid) {
            verify(service).executeOps(user, request.getDestinations(), opType, lwTypes, bsMap, lwMap, work);
            verify(service).updateSources(bcStates, lwMap.values(), Labware.State.used, barcodesToUnstore);
        } else {
            verify(service, never()).executeOps(any(), any(), any(), any(), any(), any(), any());
            verify(service, never()).updateSources(any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @MethodSource("loadLabwareTypesArgs")
    public void testLoadLabwareTypes(List<LabwareType> lwTypes, List<String> strings, String expectedProblem) {
        when(mockLwTypeRepo.findAllByNameIn(any())).thenReturn(lwTypes);
        List<SlotCopyDestination> dests = strings.stream()
                .map(string -> new SlotCopyDestination(string, null, null, null, null))
                .collect(toList());
        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertThat(service.loadLabwareTypes(problems, dests).values()).containsExactlyInAnyOrderElementsOf(lwTypes);
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
            if (!lwTypes.isEmpty()) {
                verify(mockLwTypeRepo).findAllByNameIn(new HashSet<>(strings));
            }
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    static Stream<Arguments> loadLabwareTypesArgs() {
        LabwareType plateType = EntityFactory.makeLabwareType(2, 3, "Plate");
        LabwareType tubeType = EntityFactory.getTubeType();
        final String plateName = plateType.getName();
        final String tubeName = tubeType.getName();
        return Arrays.stream(new Object[][] {
                { List.of(plateType, tubeType), List.of(plateName.toLowerCase(), plateName.toUpperCase(), tubeName.toUpperCase()), null},
                { List.of(plateType, tubeType), List.of(plateName, tubeName, "Bananas", "Bananas", "Apples"), "Unknown labware types: [\"Bananas\", \"Apples\"]"},
                { List.of(plateType, tubeType), Arrays.asList(plateName, null, tubeName), "Labware type name missing from request."},
                { List.of(plateType), List.of(plateName, "", plateName), "Labware type name missing from request."},
                { List.of(), List.of(), null },
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("loadEntityArgs")
    public void testLoadEntity(OperationType opType, String name, String problem) {
        Function<String, Optional<OperationType>> func = s -> Optional.ofNullable(opType);
        List<String> problems = new ArrayList<>();
        OperationType result = service.loadEntity(problems, name, "X", func);
        assertSame(opType, result);
        if (problem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsOnly(problem);
        }
    }

    static Stream<Arguments> loadEntityArgs() {
        OperationType opType = EntityFactory.makeOperationType("fry", null);
        return Stream.of(
                Arguments.of(null, null, "No X specified."),
                Arguments.of(null, "", "No X specified."),
                Arguments.of(null, "Custard", "Unknown X: \"Custard\""),
                Arguments.of(opType, "FRY", null),
                Arguments.of(opType, "fry", null)
        );
    }

    @ParameterizedTest
    @MethodSource("checkPreBarcodesArgs")
    public void testCheckPreBarcodes(UCMap<LabwareType> lwTypes, List<SlotCopyDestination> destinations,
                                     List<String> expectedProblems) {
        when(mockBarcodeValidator.validate(any(), any())).then(invocation -> {
            String string = invocation.getArgument(0);
            if (string!=null && string.indexOf('!')<0) {
                return true;
            }
            Consumer<String> con = invocation.getArgument(1);
            con.accept("Bad barcode: "+string);
            return false;
        });
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkPreBarcodes(problems, destinations, lwTypes);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkPreBarcodesArgs() {
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName,
                EntityFactory.makeLabwareType(1, 1, "Tube"),
                EntityFactory.makeLabwareType(1, 1, "Pretube"));
        lwTypes.get("Pretube").setPrebarcoded(true);

        final SlotCopyDestination tubeSCD = toSCD("Tube", null);
        final SlotCopyDestination pretubeSCD = toSCD("Pretube", "12345");
        return Arrays.stream(new Object[][] {
                {tubeSCD, toSCD("tube", ""), pretubeSCD},
                {toSCD("Bananas", null), tubeSCD, pretubeSCD},
                {toSCD("tube", "12345"), tubeSCD, pretubeSCD, "Prebarcode not expected for labware type: [Tube]"},
                {toSCD("pretube", null), tubeSCD, pretubeSCD, "Expected a prebarcode for labware type: [Pretube]"},
                {toSCD("pretube", ""), tubeSCD, "Expected a prebarcode for labware type: [Pretube]"},
                {toSCD("pretube", "Hi!"), "Bad barcode: HI!"},
        }).map(arr -> {
            List<?> scds = Arrays.stream(arr).filter(obj -> obj instanceof SlotCopyDestination).collect(toList());
            List<?> problems = Arrays.stream(arr).filter(obj -> obj instanceof String).collect(toList());
            return Arguments.of(lwTypes, scds, problems);
        });
    }

    static SlotCopyDestination toSCD(String lwTypeName, String prebarcode) {
        return new SlotCopyDestination(lwTypeName, prebarcode, null, null, null);
    }

    @ParameterizedTest
    @MethodSource("checkPreBarcodesInUseArgs")
    public void testCheckPreBarcodesInUse(Collection<String> prebarcodes, String expectedProblem) {
        List<SlotCopyDestination> scds = prebarcodes.stream()
                .map(bc -> toSCD("Pretube", bc))
                .collect(toList());
        doReturn(false).when(mockLwRepo).existsByBarcode(any());
        doReturn(true).when(mockLwRepo).existsByBarcode("USEDBC");
        doReturn(false).when(mockLwRepo).existsByExternalBarcode(any());
        doReturn(true).when(mockLwRepo).existsByExternalBarcode("USEDEXTBC");
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.checkPreBarcodesInUse(problems, scds);
        Matchers.assertProblem(problems, expectedProblem);
    }

    static Stream<Arguments> checkPreBarcodesInUseArgs() {
        return Arrays.stream(new Object[][] {
                {List.of("Alpha", "Beta"), null},
                {List.of(), null},
                {Arrays.asList(null, null, "", "", "Alpha", "Beta"), null},
                {List.of("Alpha", "USEDBC"), "Labware already exists with barcode USEDBC."},
                {List.of("Beta", "USEDEXTBC"), "Labware already exists with external barcode USEDEXTBC."},
                {List.of("Alpha", "Beta", "ALPHA"), "Destination barcode given multiple times: ALPHA"},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("loadSourcesArgs")
    public void testLoadSources(List<Labware> labware, SlotCopyRequest request, Set<String> expectedSourceBarcodes,
                                List<String> expectedProblems) {
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(labware);
        List<String> problems = new ArrayList<>(expectedProblems.size());
        UCMap<Labware> lwMap = service.loadSources(problems, request);
        if (!expectedSourceBarcodes.isEmpty()) {
            verify(mockLwRepo).findByBarcodeIn(expectedSourceBarcodes);
        }
        assertThat(lwMap).hasSize(labware.size());
        labware.forEach(lw -> assertSame(lw, lwMap.get(lw.getBarcode())));
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> loadSourcesArgs() {
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        String bc1 = lw1.getBarcode();
        String bc2 = lw2.getBarcode();
        List<Labware> lws = List.of(lw1, lw2);
        Set<String> bcs = Set.of(bc1, bc2);
        final List<String> ok = List.of();
        return Arrays.stream(new Object[][] {
                {lws, toSCR(List.of(bc1, bc1, bc2), List.of(bc1, bc2)), bcs, ok},
                {lws, toSCR(List.of(bc1, bc2), null), bcs, ok},
                {List.of(), toSCR(null, null), Set.of(), ok},
                {List.of(lw1), toSCR(Arrays.asList(bc1, null), null), Set.of(bc1), List.of("Missing source barcode.")},
                {List.of(lw2), toSCR(List.of(bc2, ""), null), Set.of(bc2), List.of("Missing source barcode.")},
                {lws, toSCR(List.of(bc1, bc2, ""), List.of(bc2, "BANANAS", "BANANAS")), Set.of(bc1, bc2, "BANANAS"),
                        List.of("Missing source barcode.", "Unknown source barcode: [\"BANANAS\"]")},
        }).map(Arguments::of);
    }

    static SlotCopyRequest toSCR(List<String> firstSourceBarcodes, List<String> secondSourceBarcodes) {
        if (firstSourceBarcodes==null) {
            return new SlotCopyRequest();
        }
        SlotCopyDestination dest1 = new SlotCopyDestination();
        final Address A1 = new Address(1,1);
        dest1.setContents(firstSourceBarcodes.stream().map(bc -> new SlotCopyContent(bc,A1,A1)).collect(toList()));
        List<SlotCopyDestination> scds;
        if (secondSourceBarcodes==null) {
            scds = List.of(dest1);
        } else {
            SlotCopyDestination dest2 = new SlotCopyDestination();
            dest2.setContents(secondSourceBarcodes.stream().map(bc -> new SlotCopyContent(bc, A1, A1)).collect(toList()));
            scds = List.of(dest1, dest2);
        }
        SlotCopyRequest request = new SlotCopyRequest();
        request.setDestinations(scds);
        return request;
    }

    @ParameterizedTest
    @MethodSource("checkListedSourcesArgs")
    public void testCheckListedSources(SlotCopyRequest request, UCMap<Labware.State> expectedResult,
                                       Collection<String> expectedProblems) {
        List<String> problems = new ArrayList<>(expectedProblems.size());
        assertEquals(expectedResult, service.checkListedSources(problems, request));
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkListedSourcesArgs() {
        SlotCopyRequest request0 = toSCR(null,null);
        SlotCopyRequest request1 = toSCR(List.of("STAN-1", "STAN-1"), List.of("STAN-2"));
        request1.setSources(List.of(new SlotCopySource("STAN-1", Labware.State.active),
                new SlotCopySource("stan-2", Labware.State.discarded)));
        SlotCopyRequest request2 = toSCR(List.of("STAN-1"), null);
        request2.setSources(List.of(new SlotCopySource("STAN-1", Labware.State.discarded),
                new SlotCopySource(null, Labware.State.used)));
        SlotCopyRequest request3 = toSCR(List.of("STAN-1"), null);
        request3.setSources(List.of(new SlotCopySource("STAN-1", Labware.State.used),
                new SlotCopySource("", Labware.State.discarded)));

        SlotCopyRequest request4 = toSCR(List.of("STAN-1", "STAN-2"), null);
        request4.setSources(List.of(new SlotCopySource("STAN-1", Labware.State.discarded),
                new SlotCopySource("STAN-2", null)));

        SlotCopyRequest request5 = toSCR(List.of("STAN-1", "STAN-2"), null);
        request5.setSources(List.of(new SlotCopySource("STAN-1", Labware.State.discarded),
                new SlotCopySource("STAN-1", Labware.State.discarded)));

        SlotCopyRequest request6 = toSCR(List.of("STAN-1", "STAN-2"), null);
        request6.setSources(List.of(new SlotCopySource("STAN-1", Labware.State.discarded),
                new SlotCopySource("STAN-2", Labware.State.destroyed)));

        SlotCopyRequest request7 = toSCR(List.of("STAN-1"), null);
        request7.setSources(List.of(new SlotCopySource("stan-1", Labware.State.used),
                new SlotCopySource("STAN-404", Labware.State.active)));

        return Arrays.stream(new Object[][] {
                {request0, new UCMap<>(), List.of()},
                {request1, toUCMap("STAN-1", Labware.State.active, "STAN-2", Labware.State.discarded), List.of()},
                {request2, toUCMap("STAN-1", Labware.State.discarded), List.of("Source specified without barcode.")},
                {request3, toUCMap("STAN-1", Labware.State.used), List.of("Source specified without barcode.")},
                {request4, toUCMap("STAN-1", Labware.State.discarded), List.of("Source given without labware state: STAN-2")},
                {request5, toUCMap("STAN-1", Labware.State.discarded), List.of("Repeated source barcode: STAN-1")},
                {request6, toUCMap("STAN-1", Labware.State.discarded), List.of("Unsupported new labware state: destroyed")},
                {request7, toUCMap("STAN-1", Labware.State.used, "STAN-404", Labware.State.active), List.of("Unexpected extra sources listed: [STAN-404]")},
        }).map(Arguments::of);
    }

    private static <V extends Labware.State> UCMap<V> toUCMap(String key, V value) {
        UCMap<V> map = new UCMap<>(1);
        map.put(key, value);
        return map;
    }
    private static <V extends Labware.State> UCMap<V> toUCMap(String key1, V value1, String key2, V value2) {
        UCMap<V> map = new UCMap<>(2);
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidateOps(boolean cytAssist) {
        final Address A1 = new Address(1,1);
        OperationType opType = EntityFactory.makeOperationType(cytAssist ? SlotCopyServiceImp.CYTASSIST_OP : "Transfer",
                null);
        String problem = cytAssist ? "Bad thing." : null;
        List<String> problems = new ArrayList<>(cytAssist ? 1 : 0);
        SlotCopyDestination scd1 = new SlotCopyDestination("lt1", null, null, null,
                List.of(new SlotCopyContent("STAN-1", A1, A1)));
        SlotCopyDestination scd2 = new SlotCopyDestination("lt2", null, null, null,
                List.of(new SlotCopyContent("STAN-2", A1, A1)));
        final LabwareType lt1 = EntityFactory.makeLabwareType(1, 1, "lt1");
        final LabwareType lt2 = EntityFactory.makeLabwareType(1, 1, "lt2");
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, lt1, lt2);
        if (cytAssist) {
            doAnswer(Matchers.addProblem(problem)).when(service).validateCytOp(any(), any(), same(lt2));
            doNothing().when(service).validateCytOp(any(), any(), same(lt1));
        }
        service.validateOps(problems, List.of(scd1, scd2), opType, lwTypes);
        if (cytAssist) {
            verify(service).validateCytOp(problems, scd1.getContents(), lt1);
            verify(service).validateCytOp(problems, scd2.getContents(), lt2);
            assertThat(problems).containsExactly(problem);
        } else {
            verify(service, never()).validateCytOp(any(), any(), any());
        }
    }

    @ParameterizedTest
    @MethodSource("validateCytOpArgs")
    public void testValidateCytOp(LabwareType lt, List<SlotCopyContent> sccs, String expectedProblem) {
        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.validateCytOp(problems, sccs, lt);
        Matchers.assertProblem(problems, expectedProblem);
    }

    static Stream<Arguments> validateCytOpArgs() {
        LabwareType cytLt = EntityFactory.makeLabwareType(4, 1, SlotCopyServiceImp.CYTASSIST_SLIDE);
        LabwareType cytLtXL = EntityFactory.makeLabwareType(2, 1, SlotCopyServiceImp.CYTASSIST_SLIDE_XL);
        LabwareType tubeLt = EntityFactory.getTubeType();
        SlotCopyContent[] sccs = IntStream.rangeClosed(1, 4)
                .mapToObj(r -> new SlotCopyContent(null, null, new Address(r, 1)))
                .toArray(SlotCopyContent[]::new);
        return Arrays.stream(new Object[][] {
                {cytLt, List.of(sccs[0], sccs[3]), null},
                {cytLtXL, List.of(sccs[0], sccs[1]), null},
                {tubeLt, List.of(sccs[0]), "Expected labware type Visium LP CytAssist or Visium LP CytAssist XL for operation CytAssist."},
                {cytLt, List.of(sccs[0], sccs[1]), "Slots B1 and C1 are disallowed for use in this operation."},
                {cytLt, List.of(sccs[0], sccs[2]), "Slots B1 and C1 are disallowed for use in this operation."},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidateSources(boolean ok) {
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLabwareValidatorFactory.getValidator(any())).thenReturn(val);
        final List<String> errors = ok ? List.of("Bad thing.") : List.of();
        when(val.getErrors()).thenReturn(errors);
        final List<String> problems = new ArrayList<>(errors.size());
        List<Labware> labware = List.of(EntityFactory.getTube());
        service.validateSources(problems, labware);

        assertThat(problems).containsExactlyElementsOf(errors);

        verify(val).setUniqueRequired(false);
        verify(val).setSingleSample(false);
        verify(val).validateSources();
    }

    @ParameterizedTest
    @MethodSource("validateContentsArgs")
    public void testValidateContents(UCMap<Labware> lwMap, UCMap<LabwareType> lwTypes, List<SlotCopyDestination> scds,
                                     List<String> expectedProblems) {
        List<String> problems = new ArrayList<>();
        SlotCopyRequest request = new SlotCopyRequest();
        request.setDestinations(scds);
        service.validateContents(problems, lwTypes, lwMap, request);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateContentsArgs() {
        Address A1 = new Address(1,1);
        Address A2 = new Address(1,2);
        Address A3 = new Address(1,3);
        Address B1 = new Address(2,1);
        LabwareType rowLt = EntityFactory.makeLabwareType(1, 3, "rowLt");
        LabwareType colLt = EntityFactory.makeLabwareType(3, 1, "colLt");
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, rowLt, colLt);
        Sample sample = EntityFactory.getSample();
        Labware src = EntityFactory.makeLabware(rowLt, sample, sample);
        src.setBarcode("STAN-0");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, src);
        return Arrays.stream(new Object[][] {
                {
                        List.of(makeSCD("rowLt", "STAN-0", A1, A1, "STAN-0", A2, A2),
                                makeSCD("colLt", "STAN-0", A1, A1, "STAN-0", A2, B1)),
                        List.of()
                },
                {
                    List.of(), List.of("No destinations specified.")
                },
                {
                    List.of(makeSCD("rowLt")), List.of("No contents specified in destination.")
                },
                {
                    List.of(makeSCD("rowLt", "STAN-0", A1, null)),
                        List.of("No destination address specified.")
                },
                {
                        List.of(makeSCD("rowLt", "STAN-0", A1, A1, "STAN-0", A2, A1)),
                        List.of("Repeated destination address: A1")
                },
                {
                    List.of(makeSCD("colLt", "STAN-0", A1, A2)),
                        List.of("Invalid address A2 for labware type colLt.")
                },
                {
                        List.of(makeSCD("rowLt", "STAN-0", null, A1)),
                        List.of("No source address specified.")
                },
                {
                    List.of(makeSCD("rowLt", "STAN-0", B1, A1)),
                        List.of("Invalid address B1 for source labware STAN-0.")
                },
                {
                    List.of(makeSCD("rowLt", "STAN-0", A3, A1)),
                        List.of("Slot A3 in labware STAN-0 is empty.")
                },
                {
                    List.of(makeSCD("rowLt", "STAN-404", A3, A1)), List.of()
                },
        }).map(arr -> Arguments.of(lwMap, lwTypes, arr[0], arr[1]));
    }

    private static SlotCopyDestination makeSCD(String ltName, Object... args) {
        SlotCopyDestination dest = new SlotCopyDestination();
        dest.setLabwareType(ltName);
        List<SlotCopyContent> contents = new ArrayList<>(args.length/3);
        for (int i = 0 ; i < args.length; i += 3) {
            contents.add(new SlotCopyContent((String) args[i], (Address) args[i+1], (Address) args[i+2]));
        }
        dest.setContents(contents);
        return dest;
    }

    @ParameterizedTest
    @MethodSource("validateBioStatesArgs")
    public void testValidateBioStates(List<BioState> knownBioStates, List<String> givenBsNames,
                                      Set<String> expectedBsNames,
                                      String expectedProblem) {
        List<SlotCopyDestination> dests = givenBsNames.stream()
                .map(string -> new SlotCopyDestination(null, null, null, string, null))
                .collect(toList());
        when(mockBsRepo.findAllByNameIn(any())).thenReturn(knownBioStates);
        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);

        UCMap<BioState> bsMap = service.validateBioStates(problems, dests);
        if (expectedBsNames.isEmpty()) {
            assertThat(bsMap).isEmpty();
            assertThat(problems).isEmpty();
            return;
        }
        verify(mockBsRepo).findAllByNameIn(expectedBsNames);
        assertThat(bsMap).hasSize(knownBioStates.size());
        knownBioStates.forEach(bs -> assertSame(bs, bsMap.get(bs.getName())));
        Matchers.assertProblem(problems, expectedProblem);
    }

    static Stream<Arguments> validateBioStatesArgs() {
        final String nameLibrary = SlotCopyServiceImp.BS_LIBRARY;
        final String nameCdna = SlotCopyServiceImp.BS_CDNA;
        final String nameProbes = SlotCopyServiceImp.BS_PROBES;
        BioState bsLibrary = new BioState(10, nameLibrary);
        BioState bsCdna = new BioState(11, nameCdna);
        BioState bsProbes = new BioState(12, nameProbes);
        BioState bsOther = EntityFactory.getBioState();
        final String nameOther = bsOther.getName();

        return Arrays.stream(new Object[][] {
                {List.of(bsLibrary, bsCdna, bsProbes), Arrays.asList(nameLibrary, nameCdna, nameProbes, null, nameCdna),
                        Set.of(nameLibrary, nameCdna, nameProbes), null},
                {List.of(), List.of(), Set.of(), null},
                {List.of(bsLibrary), List.of(nameLibrary, nameLibrary, "Bananas"), Set.of(nameLibrary, "Bananas"),
                        "Unknown bio state: [Bananas]"},
                {List.of(bsLibrary, bsOther), List.of(nameLibrary, nameOther, nameOther), Set.of(nameLibrary, nameOther),
                        "Bio state not allowed for this operation: ["+nameOther+"]"},
        }).map(Arguments::of);
    }

    @Test
    public void testExecuteOps() {
        final Address A1 = new Address(1,1);
        User user = EntityFactory.getUser();
        List<SlotCopyDestination> dests = List.of(
                new SlotCopyDestination("lt1", "pb1", SlideCosting.SGP, "bs1", List.of(new SlotCopyContent("STAN-0", A1, A1))),
                new SlotCopyDestination("lt2", null, SlideCosting.Faculty, null, List.of(new SlotCopyContent("STAN-1", A1, A1)))
        );
        OperationType opType = EntityFactory.makeOperationType("optype", null);
        final LabwareType lt1 = EntityFactory.makeLabwareType(1, 1, "lt1");
        final LabwareType lt2 = EntityFactory.makeLabwareType(1, 2, "lt2");
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, lt1, lt2);
        Labware lw1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        Labware lw2 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        lw1.setBarcode("STAN-0");
        lw2.setBarcode("STAN-1");
        Work work = new Work(50, "SGP50", null, null, null, null, null);
        UCMap<Labware> sources = UCMap.from(Labware::getBarcode, lw1, lw2);
        final BioState bs = new BioState(10, "bs1");
        UCMap<BioState> bsMap = UCMap.from(BioState::getName, bs);

        Operation op1 = new Operation();
        op1.setId(21);
        Operation op2 = new Operation();
        op2.setId(22);
        Labware newLw1 = EntityFactory.makeEmptyLabware(lt1);
        Labware newLw2 = EntityFactory.makeEmptyLabware(lt2);

        doReturn(new OperationResult(List.of(op1), List.of(newLw1)), new OperationResult(List.of(op2), List.of(newLw2)))
                .when(service).executeOp(any(), any(), any(), any(), any(), any(), any(), any());

        OperationResult result = service.executeOps(user, dests, opType, lwTypes, bsMap, sources, work);
        assertThat(result.getOperations()).containsExactly(op1, op2);
        assertThat(result.getLabware()).containsExactly(newLw1, newLw2);

        verify(service).executeOp(user, dests.get(0).getContents(), opType, lt1, "pb1", sources, SlideCosting.SGP, bs);
        verify(service).executeOp(user, dests.get(1).getContents(), opType, lt2, null, sources, SlideCosting.Faculty, null);

        verify(mockWorkService).link(work, result.getOperations());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    public void testExecuteOp(boolean bsInRequest, boolean bsInOpType) {
        final User user = EntityFactory.getUser();
        final BioState rbs = bsInRequest ? new BioState(5, "requestbs") : null;
        final BioState obs = bsInOpType ? new BioState(6, "opbs") : null;
        List<SlotCopyContent> contents = List.of(new SlotCopyContent());
        final SlideCosting costing = SlideCosting.SGP;
        LabwareType lt = EntityFactory.getTubeType();
        UCMap<Labware> sourceMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        Labware emptyLw = EntityFactory.makeEmptyLabware(lt);
        final String preBarcode = "pb1";
        OperationType opType = EntityFactory.makeOperationType("optype1", obs);
        Map<Integer, Sample> oldSampleIdToNewSample = Map.of(500, EntityFactory.getSample());
        when(mockLwService.create(any(), any(), any())).thenReturn(emptyLw);
        doReturn(oldSampleIdToNewSample).when(service).createSamples(any(), any(), any());
        Labware filledLw = EntityFactory.makeLabware(lt, EntityFactory.getSample());
        doReturn(filledLw).when(service).fillLabware(any(), any(), any(), any());
        Operation op = new Operation();
        op.setId(50);
        doReturn(op).when(service).createOperation(any(), any(), any(), any(), any(), any());

        OperationResult opres = service.executeOp(user, contents, opType, lt, preBarcode, sourceMap, costing, rbs);
        assertThat(opres.getLabware()).containsExactly(filledLw);
        assertThat(opres.getOperations()).containsExactly(op);

        verify(mockLwService).create(lt, preBarcode, preBarcode);
        verify(service).createSamples(contents, sourceMap, bsInRequest ? rbs : obs);
        verify(service).fillLabware(emptyLw, contents, sourceMap, oldSampleIdToNewSample);
        verify(service).createOperation(user, contents, opType, sourceMap, filledLw, oldSampleIdToNewSample);
        verify(mockLwNoteRepo).save(new LabwareNote(null, filledLw.getId(), op.getId(), "costing", costing.name()));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCreateSamples(boolean changeBioState) {
        BioState newBioState = changeBioState ? cdna : null;
        Tissue tissue1 = EntityFactory.getTissue();
        Tissue tissue2 = EntityFactory.makeTissue(tissue1.getDonor(), tissue1.getSpatialLocation());
        Sample tissueSample = new Sample(1, 1, tissue1, tissue);
        Sample cdnaSample = new Sample(2, 2, tissue2, cdna);
        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);
        final Address C1 = new Address(3,1);

        List<Labware> labware = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(slideType))
                .collect(toList());
        labware.get(0).getFirstSlot().getSamples().addAll(List.of(tissueSample, cdnaSample));
        labware.get(0).getSlot(B1).getSamples().add(tissueSample);
        labware.get(1).getFirstSlot().getSamples().add(cdnaSample);
        UCMap<Labware> lwMap = UCMap.from(labware, Labware::getBarcode);
        List<SlotCopyContent> contents = List.of(
                new SlotCopyContent(labware.get(0).getBarcode(), A1, A1),
                new SlotCopyContent(labware.get(0).getBarcode(), B1, B1),
                new SlotCopyContent(labware.get(1).getBarcode(), A1, C1)
        );
        int[] idCounter = {500};
        List<Sample> createdSamples = new ArrayList<>();
        when(mockSampleRepo.save(any())).then(invocation -> {
            Sample sam = invocation.getArgument(0);
            assertNull(sam.getId());
            sam.setId(++idCounter[0]);
            createdSamples.add(sam);
            return sam;
        });

        Map<Integer, Sample> result = service.createSamples(contents, lwMap, newBioState);

        assertEquals(2, result.size());
        if (newBioState==null) {
            assertThat(createdSamples).isEmpty();
            assertEquals(tissueSample, result.get(tissueSample.getId()));
        } else {
            assertEquals(1, createdSamples.size());
            Sample sam = createdSamples.get(0);
            assertEquals(tissueSample.getTissue(), sam.getTissue());
            assertEquals(newBioState, sam.getBioState());
            assertEquals(tissueSample.getSection(), sam.getSection());
            assertEquals(sam, result.get(tissueSample.getId()));
        }

        assertEquals(cdnaSample, result.get(cdnaSample.getId()));
    }


    @Test
    public void testFillLabware() {
        UCMap<Labware> lwMap = makeLabwareMap();
        Map<Integer, Sample> sampleMap = makeSourceSamples(cdna).stream()
                .collect(BasicUtils.toMap(Sample::getId));
        Labware lw = EntityFactory.makeEmptyLabware(plateType);
        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);
        List<SlotCopyContent> contents = List.of(
                new SlotCopyContent("STAN-001", A1, A1),
                new SlotCopyContent("STAN-001", B1, B1),
                new SlotCopyContent("STAN-002", A1, A1)
        );

        assertSame(lw, service.fillLabware(lw, contents, lwMap, sampleMap));
        verify(mockSlotRepo).saveAll(List.of(lw.getSlot(A1), lw.getSlot(B1)));
        verify(mockEntityManager).refresh(lw);

        assertThat(lw.getSlot(A1).getSamples()).containsExactlyInAnyOrder(
                sourceSamples.get(0), sourceSamples.get(1)
        );
        assertThat(lw.getSlot(B1).getSamples()).containsExactlyInAnyOrder(
                sourceSamples.get(1)
        );
    }

    @Test
    public void testCreateOperation() {
        UCMap<Labware> lwMap = makeLabwareMap();
        Sample newSample = new Sample(500, 3, EntityFactory.getTissue(), cdna);
        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);
        final Address A2 = new Address(1,2);
        final Address B2 = new Address(2,2);

        Map<Integer, Sample> sampleMap = Map.of(
                sourceSamples.get(0).getId(), sourceSamples.get(0),
                sourceSamples.get(1).getId(), sourceSamples.get(1),
                sourceSamples.get(2).getId(), newSample
        );
        List<SlotCopyContent> contents = List.of(
                new SlotCopyContent("STAN-001", A1, A2),
                new SlotCopyContent("STAN-002", B1, B2)
        );
        Labware lw1 = sourceLabware.get(0);
        Labware lw2 = sourceLabware.get(1);
        Labware destLw = EntityFactory.makeEmptyLabware(plateType);

        Operation op = new Operation(10, opType, null, null, null);
        when(mockOpService.createOperation(any(), any(), any(), isNull())).thenReturn(op);

        assertSame(op, service.createOperation(user, contents, opType, lwMap, destLw, sampleMap));

        List<Action> expectedActions = List.of(
                new Action(null, null, lw1.getSlot(A1), destLw.getSlot(A2), sourceSamples.get(0), sourceSamples.get(0)),
                new Action(null, null, lw2.getSlot(B1), destLw.getSlot(B2), newSample, sourceSamples.get(2))
        );
        verify(mockOpService).createOperation(opType, user, expectedActions, null);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings={"discarded", "used"})
    public void testUpdateSources(Labware.State defaultState) {
        UCMap<Labware.State> bcStateMap = new UCMap<>(4);
        bcStateMap.put("STAN-A", Labware.State.active);
        bcStateMap.put("STAN-U", Labware.State.used);
        bcStateMap.put("STAN-D", Labware.State.discarded);
        final LabwareType lt = EntityFactory.getTubeType();
        final Sample sample = EntityFactory.getSample();
        List<Labware> labware = "AUD0".chars().mapToObj(ch -> {
            String bc = "STAN-"+Character.toString(ch);
            Labware lw = EntityFactory.makeLabware(lt, sample);
            lw.setBarcode(bc);
            return lw;
        }).collect(toList());
        Labware lwA = labware.get(0), lwU = labware.get(1), lwD = labware.get(2), lw0 = labware.get(3);
        final Set<String> barcodesToUnstore = new HashSet<>(2);
        service.updateSources(bcStateMap, labware, defaultState, barcodesToUnstore);
        //noinspection unchecked
        ArgumentCaptor<Collection<Labware>> lwCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mockLwRepo).saveAll(lwCaptor.capture());
        Collection<Labware> saved = lwCaptor.getValue();
        assertSame(Labware.State.active, lwA.getState());
        assertSame(Labware.State.used, lwU.getState());
        assertSame(Labware.State.discarded, lwD.getState());
        if (defaultState==null) {
            assertSame(Labware.State.active, lw0.getState());
            assertThat(saved).containsExactlyInAnyOrder(lwU, lwD);
        } else {
            assertSame(defaultState, lw0.getState());
            assertThat(saved).containsExactlyInAnyOrder(lwU, lwD, lw0);
        }
        if (defaultState== Labware.State.discarded) {
            assertThat(barcodesToUnstore).containsExactlyInAnyOrder("STAN-D", "STAN-0");
        } else {
            assertThat(barcodesToUnstore).containsExactly("STAN-D");
        }
    }
}
