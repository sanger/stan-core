package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyContent;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.EntityFactory.objToList;

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
    LabwareService mockLwService;
    @Mock
    OperationService mockOpService;
    @Mock
    StoreService storeService;
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
                mockLwService, mockOpService, storeService, mockWorkService, mockLabwareValidatorFactory, mockEntityManager,
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
        SlotCopyRequest request = new SlotCopyRequest("op", "thing", List.of(), null, null);
        ValidationException ex = valid ? null : new ValidationException("Bad", List.of());
        Operation op = new Operation(200, opType, null, null, null);
        OperationResult result = valid ? new OperationResult(List.of(op), List.of()) : null;
        Matchers.mockTransactor(mockTransactor);
        (valid ? doReturn(result) : doThrow(ex)).when(service).performInsideTransaction(any(), any());
        doNothing().when(service).unstoreSources(any(), any());

        if (valid) {
            assertSame(result, service.perform(user, request));
        } else {
            assertSame(ex, assertThrows(ValidationException.class, () -> service.perform(user, request)));
        }
        verify(service).performInsideTransaction(user, request);
        // Unstore will not be executed because the optype does not have the the "discard sources" flag
        verify(service, never()).unstoreSources(any(), any());
    }

    @Test
    public void testPerformAndDiscard() {
        SlotCopyRequest request = new SlotCopyRequest("op", "thing", List.of(), null, null);
        OperationType discardingOpType = EntityFactory.makeOperationType("dot", null, OperationTypeFlag.DISCARD_SOURCE);
        Operation op = new Operation(200, discardingOpType, null, null, null);
        OperationResult result = new OperationResult(List.of(op), List.of());
        Matchers.mockTransactor(mockTransactor);
        doReturn(result).when(service).performInsideTransaction(any(), any());
        doNothing().when(service).unstoreSources(any(), any());

        assertSame(result, service.perform(user, request));
        verify(service).performInsideTransaction(user, request);
        // Unstore will be executed because the optype has the "discard sources" flag
        verify(service).unstoreSources(user, request);
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testPerformInsideTransaction(boolean valid) {
        List<SlotCopyContent> contents = List.of(new SlotCopyContent("SOURCE1", new Address(1, 2), new Address(3, 4)));
        Work work = new Work(50, "SGP5000", null, null, null, null, Work.Status.active);
        when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        SlotCopyRequest request = new SlotCopyRequest(opType.getName(), plateType.getName(), contents, work.getWorkNumber(), "pbc");
        when(mockOpTypeRepo.findByName(any())).thenReturn(Optional.of(opType));
        when(mockLwTypeRepo.findByName(any())).thenReturn(Optional.of(plateType));
        UCMap<Labware> lwMap = makeLabwareMap();
        doReturn(lwMap).when(service).loadLabware(any(), any());
        doNothing().when(service).validateLabware(any(), any());
        OperationResult opResult = new OperationResult(List.of(), List.of());
        doReturn(opResult).when(service).execute(any(), any(), any(), any(), any(), any(), any());
        if (valid) {
            doNothing().when(service).validateContents(any(), any(), any(), any());
        } else {
            doAnswer(this::addProblemAnswer).when(service).validateContents(any(), any(), any(), any());
        }
        doReturn("PBC").when(service).checkPreBarcode(any(), any(), any());
        doNothing().when(service).checkPreBarcodeInUse(any(), any());
        doNothing().when(service).validateOp(any(), any(), any(), any());
        if (valid) {
            assertSame(opResult, service.performInsideTransaction(user, request));
        } else {
            ValidationException ex = assertThrows(ValidationException.class, () -> service.performInsideTransaction(user, request));
            //noinspection unchecked
            assertThat((Collection<Object>) ex.getProblems()).containsOnly("Bananas");
        }
        verify(mockWorkService).validateUsableWork(notNull(), eq(work.getWorkNumber()));
        verify(mockOpTypeRepo).findByName(request.getOperationType());
        verify(mockLwTypeRepo).findByName(request.getLabwareType());
        verify(service).loadLabware(notNull(), same(contents));
        verify(service).validateLabware(notNull(), same(lwMap.values()));
        verify(service).validateContents(notNull(), same(plateType), same(lwMap), same(contents));
        verify(service).checkPreBarcode(notNull(), eq("pbc"), same(plateType));
        verify(service).checkPreBarcodeInUse(notNull(), eq("PBC"));
        verify(service).validateOp(notNull(), same(contents), same(opType), same(plateType));

        if (valid) {
            verify(service).execute(user, contents, opType, plateType, "PBC", lwMap, work);
        } else {
            verify(service, never()).execute(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @MethodSource("checkPrebarcodeArgs")
    public void testCheckPrebarcode(String barcode, LabwareType lwType,
                                      String expectedProblem, String expectedReturn) {
        doAnswer(invocation -> {
            String string = invocation.getArgument(0);
            Consumer<String> consumer = invocation.getArgument(1);
            if (string!=null && string.indexOf('!')>=0) {
                consumer.accept("Bad string");
                return false;
            }
            return true;
        }).when(mockBarcodeValidator).validate(any(), any());
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertEquals(expectedReturn, service.checkPreBarcode(problems, barcode, lwType));
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    static Stream<Arguments> checkPrebarcodeArgs() {
        LabwareType unPreLt = new LabwareType(1, "bottle", 1, 1, EntityFactory.getLabelType(), false);
        LabwareType preLt = new LabwareType(2, "prebottle", 1, 1, null, true);
        return Arrays.stream(new Object[][] {
                {"pbc", preLt, null, "PBC"},
                {null, unPreLt, null, null},
                {null, null, null, null},
                {null, preLt, "Expected a prebarcode for labware type prebottle.", null},
                {"", preLt, "Expected a prebarcode for labware type prebottle.", null},
                {"pbc", unPreLt, "Prebarcode not expected for labware type bottle.", "PBC"},
                {"pb!", preLt, "Bad string", "PB!"},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("validateOpArgs")
    public void testValidateOp(Collection<Address> addresses, OperationType opType, LabwareType lwType,
                               String expectedProblem) {
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        List<SlotCopyContent> contents = (addresses==null ? null
                : addresses.stream().map(ad -> new SlotCopyContent("SRC", ad, ad)).collect(toList()));
        service.validateOp(problems, contents, opType, lwType);
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    static Stream<Arguments> validateOpArgs() {
        OperationType cytOp = EntityFactory.makeOperationType(SlotCopyServiceImp.CYTASSIST_OP, null);
        OperationType otherOp = EntityFactory.makeOperationType("Bananas", null);
        LabwareType cytSlide = EntityFactory.makeLabwareType(1, 4, SlotCopyServiceImp.CYTASSIST_SLIDE);
        LabwareType cytSlidexl = EntityFactory.makeLabwareType(1, 2, SlotCopyServiceImp.CYTASSIST_SLIDE_XL);
        LabwareType otherLw = EntityFactory.getTubeType();
        Address A1 = new Address(1,1);
        Address B1 = new Address(2,1);
        Address C1 = new Address(3,1);
        Address D1 = new Address(4,1);
        return Arrays.stream(new Object[][] {
                {List.of(A1, D1), cytOp, cytSlide, null},
                {List.of(A1, B1), cytOp, cytSlidexl, null},
                {List.of(A1, B1, C1), otherOp, otherLw, null},
                {List.of(A1, B1), cytOp, cytSlide, "Slots B1 and C1 are disallowed for use in this operation."},
                {List.of(A1), cytOp, otherLw, String.format("Expected labware type %s or %s for operation %s.",
                        SlotCopyServiceImp.CYTASSIST_SLIDE, SlotCopyServiceImp.CYTASSIST_SLIDE_XL, SlotCopyServiceImp.CYTASSIST_OP)},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @CsvSource({"false,false,", "true,false,Labware already exists with barcode PBC.",
            "false,true,Labware already exists with external barcode PBC."})
    public void testCheckPreBarcodeInUse(boolean usedAsBarcode, boolean usedAsExternal, String expectedProblem) {
        String barcode = "PBC";
        when(mockLwRepo.existsByBarcode(barcode)).thenReturn(usedAsBarcode);
        when(mockLwRepo.existsByExternalBarcode(barcode)).thenReturn(usedAsExternal);
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.checkPreBarcodeInUse(problems, barcode);
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    @Test
    public void testUnstoreSources() {
        SlotCopyRequest request = new SlotCopyRequest("opType", "lwType",
                List.of(new SlotCopyContent("Alpha", null, null),
                        new SlotCopyContent("Beta", null, null),
                        new SlotCopyContent("ALPHA", null, null)),
                null, null);
        service.unstoreSources(user, request);
        verify(storeService).discardStorage(user, Set.of("ALPHA", "BETA"));
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
    @MethodSource("loadLabwareArgs")
    public void testLoadLabware(Object lwObj, Object bcObj, Object problemObj) {
        List<Labware> labware = objToList(lwObj);
        List<String> barcodes = objToList(bcObj);
        List<String> expectedProblems = objToList(problemObj);
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(labware);
        List<SlotCopyContent> contents = barcodes.stream()
                .map(bc -> new SlotCopyContent(bc, null, null))
                .collect(toList());
        HashSet<String> problems = new HashSet<>();
        UCMap<Labware> expectedMap = UCMap.from(labware, Labware::getBarcode);
        assertEquals(expectedMap, service.loadLabware(problems, contents));
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        Set<String> bcSet = barcodes.stream()
                .filter(bc -> bc!=null && !bc.isEmpty())
                .map(String::toUpperCase)
                .collect(toSet());
        if (!bcSet.isEmpty()) {
            verify(mockLwRepo).findByBarcodeIn(bcSet);
        }
    }

    static Stream<Arguments> loadLabwareArgs() {
        Labware lw = EntityFactory.getTube();
        return Stream.of(
                Arguments.of(null, List.of(), null),
                Arguments.of(null, List.of(""), "Missing source barcode."),
                Arguments.of(null, Arrays.asList(null, null), "Missing source barcode."),
                Arguments.of(lw, lw.getBarcode(), null),
                Arguments.of(lw, List.of(lw.getBarcode(), lw.getBarcode().toLowerCase()), null),
                Arguments.of(lw, List.of(lw.getBarcode(), ""), "Missing source barcode."),
                Arguments.of(lw, List.of(lw.getBarcode(), "RHUBARB", "CUSTARD"), "Unknown source barcodes: [\"RHUBARB\", \"CUSTARD\"]"),
                Arguments.of(lw, List.of(lw.getBarcode(), "", lw.getBarcode(), "RHUBARB"),
                        List.of("Missing source barcode.", "Unknown source barcode: [\"RHUBARB\"]"))
        );
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testValidateLabware(boolean valid) {
        LabwareValidator mockValidator = mock(LabwareValidator.class);
        when(mockLabwareValidatorFactory.getValidator(any())).thenReturn(mockValidator);
        Set<String> problems = new HashSet<>();
        List<String> validatorErrors = valid ? List.of() : List.of("Bad.");
        when(mockValidator.getErrors()).thenReturn(validatorErrors);
        List<Labware> sources = makeSourceLabware();
        service.validateLabware(problems, sources);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(validatorErrors);

        verify(mockValidator).setUniqueRequired(false);
        verify(mockValidator).setSingleSample(false);
        verify(mockValidator).validateSources();
    }

    @ParameterizedTest
    @MethodSource("validateContentsArgs")
    public void testValidateContents(Object objContents, Object objExpectedProblems) {
        List<SlotCopyContent> contents = objToList(objContents);
        List<String> expectedProblems = objToList(objExpectedProblems);

        Set<String> problems = new HashSet<>();
        service.validateContents(problems, plateType, makeLabwareMap(), contents);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateContentsArgs() {
        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);
        final Address C1 = new Address(3, 1);
        final Address H12 = new Address(8,12);
        final Address H13 = new Address(8, 13);
        final Address I12 = new Address(9, 12);
        final String BC1 = "STAN-001";
        final String BC2 = "STAN-002";
        final String BCX = "STAN-404";
        return Stream.of(
                Arguments.of(null, "No contents specified."),
                Arguments.of(List.of(new SlotCopyContent(BC1, A1, A1), new SlotCopyContent(BC2, B1, H12)), null),
                Arguments.of(new SlotCopyContent(BCX, A1, A1), null),
                Arguments.of(new SlotCopyContent(BC1, A1, null), "No destination address specified."),
                Arguments.of(List.of(new SlotCopyContent(BC1, A1, A1), new SlotCopyContent(BC2, A1, A1)), "Repeated destination address: A1"),
                Arguments.of(List.of(new SlotCopyContent(BC1, A1, A1), new SlotCopyContent(BC1, B1, H13), new SlotCopyContent(BC2, A1, I12)),
                        List.of("Invalid address H13 for labware type platetype.", "Invalid address I12 for labware type platetype.")),
                Arguments.of(new SlotCopyContent(BC1, null, A1), "No source address specified."),
                Arguments.of(List.of(new SlotCopyContent(BC1, A1, A1), new SlotCopyContent(BC1, H12, B1), new SlotCopyContent(BC2, I12, H12)),
                        List.of("Invalid address H12 for source labware "+BC1,
                                "Invalid address I12 for source labware "+BC2)),
                Arguments.of(List.of(new SlotCopyContent(BC1, A1, A1),
                        new SlotCopyContent(BC1, C1, B1),
                        new SlotCopyContent(BC2, C1, C1)),
                        List.of("Slot C1 in labware "+BC1+" is empty.",
                                "Slot C1 in labware "+BC2+" is empty.")),

                Arguments.of(List.of(new SlotCopyContent(BC1, null, null),
                        new SlotCopyContent(BC1, C1, A1),
                        new SlotCopyContent(BC1, H12, H13),
                        new SlotCopyContent(BC1, A1, B1),
                        new SlotCopyContent(BC2, A1, B1)),
                        List.of("No destination address specified.",
                                "Repeated destination address: B1",
                                "Invalid address H13 for labware type platetype.",
                                "No source address specified.",
                                "Invalid address H12 for source labware "+BC1,
                                "Slot C1 in labware "+BC1+" is empty."))
        );
    }

    @ParameterizedTest
    @ValueSource(strings={"DISCARD_SOURCE", "MARK_SOURCE_USED"})
    public void testExecute(OperationTypeFlag opFlag) {
        UCMap<Labware> lwMap = makeLabwareMap();

        opType.setFlags(opFlag==null ? 0 : opFlag.bit());
        Labware emptyLw = EntityFactory.makeEmptyLabware(plateType);
        doReturn(emptyLw).when(mockLwService).create(any(LabwareType.class), any(), any());
        Map<Integer, Sample> sampleMap = Map.of(1, sourceSamples.get(0));
        doReturn(sampleMap).when(service).createSamples(any(), any(), any());
        Labware filledLabware = EntityFactory.makeLabware(plateType, sourceSamples.get(0));
        doReturn(filledLabware).when(service).fillLabware(any(), any(), any(), any());
        doNothing().when(service).discardSources(any());
        Operation op = new Operation(100, opType, null, null, null);
        doReturn(op).when(service).createOperation(any(), any(), any(), any(), any(), any());
        final Address A1 = new Address(1,1);
        Work work = new Work(14, "SGP5000", null, null, null, null, Work.Status.active);
        final String preBarcode = "PBC";

        List<SlotCopyContent> contents = List.of(new SlotCopyContent("STAN-001", A1, A1));

        OperationResult result = service.execute(user, contents, opType, plateType, preBarcode, lwMap, work);

        verify(mockLwService).create(plateType, preBarcode, preBarcode);
        verify(service).createSamples(contents, lwMap, cdna);
        verify(service).fillLabware(emptyLw, contents, lwMap, sampleMap);
        boolean discard = (opFlag==OperationTypeFlag.DISCARD_SOURCE);
        boolean used = (opFlag==OperationTypeFlag.MARK_SOURCE_USED);
        verify(service, times(discard ? 1 : 0)).discardSources(lwMap.values());
        verify(service, times(used ? 1 : 0)).markSourcesUsed(lwMap.values());
        verify(service).createOperation(user, contents, opType, lwMap, filledLabware, sampleMap);
        verify(mockWorkService).link(work, List.of(op));

        assertEquals(result.getLabware(), List.of(filledLabware));
        assertEquals(result.getOperations(), List.of(op));
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
                .collect(toMap(Sample::getId, s -> s));
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

    @Test
    public void testDiscardSources() {
        List<Labware> labware = IntStream.range(0,3)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(slideType))
                .collect(toList());
        service.discardSources(labware);
        for (Labware lw : labware) {
            assertTrue(lw.isDiscarded());
        }
        verify(mockLwRepo).saveAll(labware);
    }

    @Test
    public void testMarkSourcesUsed() {
        List<Labware> labware = IntStream.range(0,3)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(slideType))
                .collect(toList());
        service.markSourcesUsed(labware);
        for (Labware lw : labware) {
            assertTrue(lw.isUsed());
        }
        verify(mockLwRepo).saveAll(labware);
    }

    private Object addProblemAnswer(InvocationOnMock invocation) {
        Collection<String> problems = invocation.getArgument(0);
        problems.add("Bananas");
        return null;
    }

}
