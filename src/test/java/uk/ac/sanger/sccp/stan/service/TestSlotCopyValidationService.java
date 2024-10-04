package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.*;
import uk.ac.sanger.sccp.stan.service.SlotCopyValidationService.Data;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.utils.UCMap;
import uk.ac.sanger.sccp.utils.Zip;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Tests {@link SlotCopyValidationServiceImp}
 */
public class TestSlotCopyValidationService {
    @Mock
    LabwareTypeRepo mockLwTypeRepo;
    @Mock
    LabwareRepo mockLwRepo;
    @Mock
    BioStateRepo mockBsRepo;
    @Mock
    ValidationHelperFactory mockValHelperFactory;
    @Mock
    Validator<String> mockPreBarcodeValidator;
    @Mock
    Validator<String> mockLotNumberValidator;
    @Mock
    CleanedOutSlotService mockCleanedOutSlotService;

    SlotCopyValidationServiceImp service;
    AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(new SlotCopyValidationServiceImp(mockLwTypeRepo, mockLwRepo, mockBsRepo, mockValHelperFactory,
                mockPreBarcodeValidator, mockLotNumberValidator, mockCleanedOutSlotService));
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidate_valid(boolean valid) {
        User user = valid ? EntityFactory.getUser() : null;
        SlotCopyRequest request = new SlotCopyRequest("optype", "sgp1",
                null, List.of(new SlotCopySource("STAN-1", Labware.State.active)),
                List.of(new SlotCopyDestination("lt1", "pb1", SlideCosting.Faculty, "lot1",
                        "probelot1", List.of(new SlotCopyContent("STAN1", new Address(1,1), new Address(1,2))),
                        "bs1", "LP3")));
        Data data = new Data(request);
        ValidationHelper val = mock(ValidationHelper.class);
        when(mockValHelperFactory.getHelper()).thenReturn(val);
        OperationType opType = EntityFactory.makeOperationType("optype", null);
        doReturn(opType).when(val).checkOpType(any());
        UCMap<Labware> destLw = UCMap.from(Labware::getBarcode, EntityFactory.makeEmptyLabware(EntityFactory.getTubeType()));
        doReturn(destLw).when(service).loadExistingDestinations(any(), any(), any());
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, EntityFactory.getTubeType());
        mayAddProblem(valid ? null : "bad lw type", lwTypes).when(service).loadLabwareTypes(any(), any());
        mayAddProblem(valid ? null : "bad prebc").when(service).checkPreBarcodes(any(), any(), any());
        mayAddProblem(valid ? null : "used prebc").when(service).checkPreBarcodesInUse(any(), any(), any());
        UCMap<Labware> sourceLw = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        mayAddProblem(valid ? null : "bad source", sourceLw).when(service).loadSources(any(), any(), any());
        mayAddProblem(valid ? null : "bad listed souce").when(service).checkListedSources(any(), any());
        mayAddProblem(valid ? null : "bad lot").when(service).validateLotNumbers(any(), any());
        mayAddProblem(valid ? null : "bad contents").when(service).validateContents(any(), any(), any(), any(), any());
        mayAddProblem(valid ? null : "bad ops").when(service).validateOps(any(), any(), any(), any());
        mayAddProblem(valid ? null : "bad lp").when(service).validateLpNumbers(any(), any());
        UCMap<BioState> bsMap = UCMap.from(BioState::getName, EntityFactory.getBioState());
        mayAddProblem(valid ? null : "bad bs", bsMap).when(service).validateBioStates(any(), any());
        Work work = EntityFactory.makeWork("SGP1");
        doReturn(work).when(val).checkWork(anyString());
        doReturn(valid ? Set.of() : Set.of("val problem")).when(val).getProblems();
        service.validate(user, data);
        final Collection<String> problems = data.problems;
        if (valid) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactlyInAnyOrder(
                    "No user supplied.", "val problem", "bad lw type", "bad prebc", "bad source",
                    "bad listed souce", "bad lot", "bad contents", "bad ops", "bad bs", "used prebc", "bad lp"
            );
        }

        verify(val).checkOpType(request.getOperationType());
        List<SlotCopyDestination> scds = request.getDestinations();
        verify(service).loadExistingDestinations(val, opType, scds);
        verify(service).loadLabwareTypes(problems, scds);
        verify(service).checkPreBarcodes(problems, scds, lwTypes);
        verify(service).checkPreBarcodesInUse(problems, scds, destLw);
        verify(service).loadSources(problems, val, scds);
        verify(service).checkListedSources(problems, request);
        verify(service).validateLotNumbers(problems, scds);
        verify(service).validateContents(problems, lwTypes, sourceLw, destLw, request);
        verify(service).validateOps(problems, scds, opType, lwTypes);
        verify(service).validateBioStates(problems, scds);
        verify(service).validateLpNumbers(problems, scds);
        verify(val).checkWork(request.getWorkNumber());
        assertSame(data.destLabware, destLw);
        assertSame(data.sourceLabware, sourceLw);
        assertSame(data.bioStates, bsMap);
        assertSame(data.opType, opType);
        assertSame(data.work, work);
        assertSame(data.lwTypes, lwTypes);
    }

    @Test
    public void testValidate_noRequest() {
        final Data data = new Data(null);
        service.validate(EntityFactory.getUser(), data);
        assertProblem(data.problems, "No request supplied.");
    }

    @ParameterizedTest
    @CsvSource({
            "LT1/LT2,",
            "LT1//LT2, Labware type missing from request.",
            "LTX/LT1/LT2, Unknown labware type: [LTX]",
    })
    public void testLoadLabwareTypes(String joinedLtNames, String expectedProblem) {
        String[] ltNames = joinedLtNames.split("/");
        final List<LabwareType> lts = IntStream.rangeClosed(1,3)
                .mapToObj(i -> EntityFactory.makeLabwareType(1,1, "LT"+i))
                .toList();
        Set<String> nonNullNames = Arrays.stream(ltNames)
                .filter(name -> !nullOrEmpty(name))
                .collect(toSet());
        when(mockLwTypeRepo.findAllByNameIn(any())).thenReturn(lts);
        UCMap<LabwareType> ltMap = UCMap.from(lts, LabwareType::getName);
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        List<SlotCopyDestination> scds = Arrays.stream(ltNames)
                .map(name -> new SlotCopyDestination(name, null, null, null, null, null, null, null))
                .toList();
        assertEquals(ltMap, service.loadLabwareTypes(problems, scds));
        assertProblem(problems, expectedProblem);
        verify(mockLwTypeRepo).findAllByNameIn(nonNullNames);
    }

    @ParameterizedTest
    @CsvSource({
            ",false,false",
            ",true,true",
            "Reusing existing destinations is not supported for operation type opname.,true,false",
    })
    public void testLoadExistingDestinations(String expectedProblem, boolean anySupplied, boolean reuseAllowed) {
        List<String> barcodes;
        Labware lw = null;
        if (!anySupplied) {
            barcodes = Arrays.asList(null, null);
        } else {
            barcodes = List.of("", "STAN-1");
            lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType(), "STAN-1");
        }
        OperationType opType;
        if (reuseAllowed) {
            opType =EntityFactory.makeOperationType("opname", null, OperationTypeFlag.ACTIVE_DEST);
        } else {
            opType = EntityFactory.makeOperationType("opname", null);
        }
        List<SlotCopyDestination> scds = barcodes.stream()
                .map(bc -> new SlotCopyDestination())
                .toList();
        if (anySupplied) {
            for (int i = 0; i < scds.size(); ++i) {
                scds.get(i).setBarcode(barcodes.get(i));
            }
        }
        ValidationHelper val = mock(ValidationHelper.class);
        UCMap<Labware> lwMap;
        if (lw!=null) {
            lwMap = UCMap.from(Labware::getBarcode, lw);
            when(val.loadActiveDestinations(anyCollection())).thenReturn(lwMap);
        } else {
            lwMap = new UCMap<>(0);
        }
        final Set<String> problems = new HashSet<>(expectedProblem==null ? 0 : 1);
        when(val.getProblems()).thenReturn(problems);
        assertEquals(lwMap, service.loadExistingDestinations(val, opType, scds));
        assertProblem(problems, expectedProblem);
        if (anySupplied) {
            verify(val).loadActiveDestinations(List.of("STAN-1"));
        } else {
            verifyNoInteractions(val);
        }
    }

    @ParameterizedTest
    @MethodSource("checkPreBarcodesArgs")
    public void testCheckPreBarcodes(String expectedProblem, List<SlotCopyDestination> scds,
                                     UCMap<LabwareType> lwTypes, List<String> expectedValidations) {
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        when(mockPreBarcodeValidator.validate(any(), any())).thenReturn(true);
        service.checkPreBarcodes(problems, scds, lwTypes);
        if (expectedValidations==null) {
            verifyNoInteractions(mockPreBarcodeValidator);
        } else {
            for (String preBc : expectedValidations) {
                verify(mockPreBarcodeValidator).validate(eq(preBc), any());
            }
            verify(mockPreBarcodeValidator, times(expectedValidations.size())).validate(any(), any());
        }
        assertProblem(problems, expectedProblem);
    }

    static Stream<Arguments> checkPreBarcodesArgs() {
        LabwareType ltp = EntityFactory.makeLabwareType(1,1,"ltp");
        LabwareType ltu = EntityFactory.makeLabwareType(1,1,"ltu");
        ltp.setPrebarcoded(true);
        ltu.setPrebarcoded(false);
        UCMap<LabwareType> lts = UCMap.from(LabwareType::getName, ltp, ltu);
        SlotCopyDestination validp = new SlotCopyDestination("ltp", "prebc", null, null, null, null, null, null);
        SlotCopyDestination validu = new SlotCopyDestination("ltu", null, null, null, null, null, null, null);
        SlotCopyDestination invalidp = new SlotCopyDestination("ltp", null, null, null, null, null, null, null);
        SlotCopyDestination invalidu = new SlotCopyDestination("ltu", "bananas", null, null, null, null, null, null);

        return Arrays.stream(new Object[][] {
                {null, List.of(validp), List.of("PREBC")},
                {null, List.of(validu)},
                {null, List.of(validp, validu), List.of("PREBC")},
                {"Expected a prebarcode for labware type: [ltp]", List.of(validu, invalidp)},
                {"Prebarcode not expected for labware type: [ltu]", List.of(validu, invalidu)},
        }).map(arr -> Arguments.of(arr[0], arr[1], lts, arr.length > 2 ? arr[2] : null));
    }

    @ParameterizedTest
    @MethodSource("checkPreBarcodesInUseArgs")
    public void testCheckPreBarcodesInUse(String expectedProblem,
                                          List<String> preBarcodes,
                                          List<Labware> labware,
                                          Set<String> existingPrebcs,
                                          Set<String> existingBcs) {
        List<SlotCopyDestination> scds = IntStream.range(0, preBarcodes.size())
                .mapToObj(i -> {
                    String preBarcode = preBarcodes.get(i);
                    SlotCopyDestination scd = new SlotCopyDestination();
                    scd.setPreBarcode(preBarcode);
                    scd.setBarcode(labware==null || labware.size() <= i ? null : labware.get(i).getBarcode());
                    return scd;
                }).toList();
        UCMap<Labware> existingDestinations;
        if (nullOrEmpty(labware)) {
            existingDestinations = new UCMap<>(0);
        } else {
            existingDestinations = UCMap.from(labware, Labware::getBarcode);
        }
        if (existingBcs!=null) {
            when(mockLwRepo.existsByBarcode(any())).thenAnswer(invocation -> existingBcs.contains(invocation.<String>getArgument(0)));
        }
        if (existingPrebcs!=null) {
            when(mockLwRepo.existsByExternalBarcode(any())).thenAnswer(invocation -> existingPrebcs.contains(invocation.<String>getArgument(0)));
        }
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.checkPreBarcodesInUse(problems, scds, existingDestinations);
        assertProblem(problems, expectedProblem);
    }

    static Stream<Arguments> checkPreBarcodesInUseArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt, "STAN-1");
        Labware lw2 = EntityFactory.makeEmptyLabware(lt, "STAN-2");
        lw1.setExternalBarcode("XB1");

        return Arrays.stream(new Object[][] {
                {null, List.of("", "PREBC1")},
                        {null, List.of("XB1"), List.of(lw1)},
                        {"External barcode \"PREBC1\" cannot be added to existing labware STAN-1.",
                        List.of("PREBC1"), List.of(lw1)},
                        {"External barcode \"PREBC1\" cannot be added to existing labware STAN-2.",
                                List.of("PREBC1"), List.of(lw2)},
                        {"External barcode given multiple times: PREBC1", List.of("", "PREBC1", "", "PREBC1")},
                        {"Labware already exists with barcode BC.", List.of("PREBC1", "BC"), null, null, Set.of("BC")},
                        {"Labware already exists with external barcode PREBC.", List.of("PREBC1", "PREBC"), null, Set.of("PREBC")},
        }).map(arr -> arr.length == 5 ? arr : Arrays.copyOf(arr, 5))
        .map(Arguments::of);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadSources(boolean anyMissing) {
        List<String> barcodes;
        if (anyMissing) {
            barcodes = List.of("STAN-1", "STAN-2", "");
        } else {
            barcodes = List.of("STAN-1", "STAN-2", "stan-1");
        }
        List<SlotCopyContent> sccs = barcodes.stream()
                .map(bc -> new SlotCopyContent(bc, null, null))
                .toList();
        Set<String> expectedCheck = Set.of("STAN-1", "STAN-2");
        List<String> problems = new ArrayList<>(anyMissing ? 1 : 0);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        ValidationHelper val = mock(ValidationHelper.class);
        when(val.checkLabware(any())).thenReturn(lwMap);
        List<SlotCopyDestination> scds = List.of(new SlotCopyDestination());
        scds.getFirst().setContents(sccs);
        assertSame(lwMap, service.loadSources(problems, val, scds));
        assertProblem(problems, anyMissing ? "Missing source barcode." : null);
        verify(val).checkLabware(expectedCheck);
    }

    @Test
    public void testCheckListedSources_request() {
        List<SlotCopySource> scss = List.of(new SlotCopySource("STAN-1", Labware.State.active));
        SlotCopyDestination scd = new SlotCopyDestination();
        List<SlotCopyContent> sccs = List.of(
                new SlotCopyContent("STAN-1", null, null),
                new SlotCopyContent(),
                new SlotCopyContent("",null,null),
                new SlotCopyContent("stan-1",null,null),
                new SlotCopyContent("stan-2", null, null)
        );
        scd.setContents(sccs);
        List<String> problems = new ArrayList<>(0);
        doNothing().when(service).checkListedSources(any(), any(), any());
        SlotCopyRequest request = new SlotCopyRequest();
        request.setDestinations(List.of(scd));
        request.setSources(scss);
        service.checkListedSources(problems, request);
        assertThat(problems).isEmpty();
        verify(service).checkListedSources(problems, scss, Set.of("STAN-1", "STAN-2"));
    }

    @Test
    public void testCheckListedSources_request_empty() {
        List<String> problems = new ArrayList<>(0);
        service.checkListedSources(problems, new SlotCopyRequest());
        assertThat(problems).isEmpty();
        verify(service, never()).checkListedSources(any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("checkListedSourcesArgs")
    public void testCheckListedSources(String expectedProblem,
                                       Collection<SlotCopySource> scss,
                                       Set<String> usedSourceBarcodes) {
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.checkListedSources(problems, scss, usedSourceBarcodes);
        assertProblem(problems, expectedProblem);
    }

    static Stream<Arguments> checkListedSourcesArgs() {
        Set<String> usedSourceBarcodes = Set.of("STAN-1", "STAN-2");

        return Arrays.stream(new Object[][]{
                {null, List.of()},
                {null, List.of(
                        new SlotCopySource("STAN-1", Labware.State.active),
                        new SlotCopySource("STAN-2", Labware.State.discarded)
                )},
                {null, List.of(new SlotCopySource("STAN-1", Labware.State.used))},
                {"Source specified without barcode.", List.of(
                        new SlotCopySource("STAN-1", Labware.State.active),
                        new SlotCopySource(null, Labware.State.discarded)
                )},
                {"Source specified without labware state: STAN-1", List.of(
                        new SlotCopySource("STAN-1", null),
                        new SlotCopySource("STAN-2", Labware.State.active)
                )},
                {"Unsupported new labware state: released", List.of(
                        new SlotCopySource("STAN-1", Labware.State.active),
                        new SlotCopySource("STAN-2", Labware.State.released)
                )},
                {"Source barcodes specified that do not map to any destination slots: [STAN-3, STAN-4]", List.of(
                        new SlotCopySource("STAN-1", Labware.State.active),
                        new SlotCopySource("STAN-3", Labware.State.active),
                        new SlotCopySource("STAN-4", Labware.State.discarded)
                )},
        }).map(arr -> Arguments.of(arr[0], arr[1], usedSourceBarcodes));
    }

    @Test
    public void testValidateLotNumbers() {
        List<SlotCopyDestination> scds = IntStream.range(0,4).mapToObj(i -> new SlotCopyDestination()).toList();
        scds.get(0).setLotNumber("LOT0");
        scds.get(1).setProbeLotNumber("PROBE1");
        scds.get(2).setLotNumber("LOT2");
        scds.get(2).setProbeLotNumber("PROBE2");
        List<String> expected = List.of("LOT0", "PROBE1", "LOT2", "PROBE2");
        when(mockLotNumberValidator.validate(any(), any())).thenReturn(true);
        List<String> problems = new ArrayList<>(0);
        service.validateLotNumbers(problems, scds);
        verify(mockLotNumberValidator, times(expected.size())).validate(any(), any());
        expected.forEach(lot -> verify(mockLotNumberValidator).validate(eq(lot), any()));
    }

    private SlotCopyDestination makeSCD(String barcode, String ltName, List<SlotCopyContent> contents) {
        SlotCopyDestination scd = new SlotCopyDestination();
        scd.setBarcode(barcode);
        scd.setLabwareType(ltName);
        scd.setContents(contents);
        return scd;
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidateLpNumbers(boolean valid) {
        String[] givenLps, expectedLps;
        String expectedProblem;
        if (valid) {
            givenLps = new String[] {null, "", "  ", "LP1", "   lp2  ", "3", "   4 "};
            expectedLps = new String[] {null, null, null, "LP1", "LP2", "LP3", "LP4"};
            expectedProblem = null;
        } else {
            givenLps = new String[] {null, "", "  LP1 ", "2", "  bananas", "L5", "-6", "lp123456789"};
            expectedLps = new String[] {null, null, "LP1", "LP2", "  bananas", "L5", "-6", "lp123456789"};
            expectedProblem = "Unrecognised format for LP number: [\"BANANAS\", \"L5\", \"-6\", \"LP123456789\"]";
        }
        List<SlotCopyDestination> scds = Arrays.stream(givenLps)
                .map(lp -> new SlotCopyDestination(null, null, null, null, null, null, null, lp))
                .toList();
        List<String> problems = new ArrayList<>(valid ? 0 : 1);
        service.validateLpNumbers(problems, scds);
        assertThat(scds).hasSize(expectedLps.length);
        Zip.forEach(scds.stream(), Arrays.stream(expectedLps), (scd, lp) -> assertEquals(lp, scd.getLpNumber()));
        assertProblem(problems, expectedProblem);
    }

    @Test
    public void testValidateContents_noDestinations() {
        List<String> problems = new ArrayList<>(1);
        service.validateContents(problems, null, null, null, new SlotCopyRequest());
        assertProblem(problems, "No destinations specified.");
        verify(service, never()).checkCleanedOutDestinations(any(), any(), any());
    }

    @Test
    public void testValidateContents() {
        LabwareType lt = EntityFactory.makeLabwareType(1,3, "lt");
        UCMap<LabwareType> lts = UCMap.from(LabwareType::getName, lt);
        Sample sample = EntityFactory.getSample();
        Labware destLw = EntityFactory.makeLabware(lt, sample);
        destLw.setBarcode("STAN-D");
        UCMap<Labware> existingDestinations = UCMap.from(Labware::getBarcode, destLw);
        Labware sourceLw = EntityFactory.makeLabware(lt, sample);
        sourceLw.setBarcode("STAN-S");
        UCMap<Labware> sourceMap = UCMap.from(Labware::getBarcode, sourceLw);
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final Address A3 = new Address(1,3);
        final Address A4 = new Address(1,4);
        // STAN-1: no contents
        // STAN-2: OK
        // STAN-3: Repeated content
        // STAN-D: Missing dest address; from lw, no such slot; from lw, filled slot
        // STAN-5: from lt, no such slot
        // STAN-6: missing source address; from source lw, no such slot; from source lw, empty slot

        List<SlotCopyDestination> scds = List.of(
                makeSCD("STAN-1", "lt", (List<SlotCopyContent>) null),
                makeSCD("STAN-2", "lt", List.of(
                        new SlotCopyContent("STAN-S", A1, A1),
                        new SlotCopyContent("BANANAS", A1, A2)
                )),
                makeSCD("STAN-3", "lt", List.of(
                        new SlotCopyContent("STAN-S", A1, A1),
                        new SlotCopyContent("STAN-S", A1, A1)
                )),
                makeSCD("STAN-D", "lt", List.of(
                        new SlotCopyContent("STAN-S", A1, null),
                        new SlotCopyContent("STAN-S", A1, A4),
                        new SlotCopyContent("STAN-S", A1, A1)
                )),
                makeSCD("STAN-5", "lt", List.of(
                        new SlotCopyContent("STAN-S", A1, A1),
                        new SlotCopyContent("STAN_S", A1, A4)
                )),
                makeSCD("STAN-6", "lt", List.of(
                        new SlotCopyContent("STAN-S", null, A1),
                        new SlotCopyContent("STAN-S", A4, A2),
                        new SlotCopyContent("STAN-S", A2, A3)
                ))
        );

        SlotCopyRequest request = new SlotCopyRequest();
        request.setDestinations(scds);
        doAnswer(addProblem("Cleaned out slots.")).when(service).checkCleanedOutDestinations(any(), any(), any());
        final List<String> problems = new ArrayList<>(10);
        service.validateContents(problems, lts, sourceMap, existingDestinations, request);
        assertThat(problems).containsExactlyInAnyOrder(
                "No contents specified in destination.",
                "Repeated copy specified: {sourceBarcode=\"STAN-S\", sourceAddress=A1, destinationAddress=A1}",
                "No destination address specified.",
                "No such slot A4 in labware STAN-D.",
                "Slot A1 in labware STAN-D is not empty.",
                "Invalid address A4 for labware type lt.",
                "No source address specified.",
                "Invalid address A4 for source labware STAN-S.",
                "Slot A2 in labware STAN-S is empty.",
                "Cleaned out slots."
        );
        verify(service).checkCleanedOutDestinations(problems, request, existingDestinations);
    }

    @Test
    public void testCheckCleanedOutDestinations_noDestinations() {
        SlotCopyRequest request = new SlotCopyRequest();
        UCMap<Labware> dests = new UCMap<>();
        List<String> problems = new ArrayList<>(0);
        service.checkCleanedOutDestinations(problems, request, dests);
        assertThat(problems).isEmpty();
        verifyNoInteractions(mockCleanedOutSlotService);
    }

    @Test
    public void testCheckCleanedOutDestinations_noCleanedOutSlots() {
        SlotCopyRequest request = new SlotCopyRequest();
        Labware lw = EntityFactory.getTube();
        UCMap<Labware> dests = UCMap.from(Labware::getBarcode, lw);
        List<String> problems = new ArrayList<>(0);
        when(mockCleanedOutSlotService.findCleanedOutSlots(any())).thenReturn(Set.of());
        service.checkCleanedOutDestinations(problems, request, dests);
        assertThat(problems).isEmpty();
        verify(mockCleanedOutSlotService).findCleanedOutSlots(dests.values());
    }

    @Test
    public void testCheckCleanedOutDestinations() {
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware[] lws = IntStream.range(0,3)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lt))
                .toArray(Labware[]::new);
        for (int i = 0; i < lws.length; ++i) {
            lws[i].setBarcode("STAN-"+i);
        }
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        List<SlotCopyDestination> scds = IntStream.range(0,4).mapToObj(i -> new SlotCopyDestination()).toList();
        Address A1 = new Address(1,1), A2 = new Address(1,2);
        Address[][] addresses = {
                {A1,A2}, {A1}, {A2, null}, {A1,A2},
        };
        for (int i = 0; i < scds.size(); ++i) {
            scds.get(i).setBarcode("STAN-"+i);
            final List<SlotCopyContent> contents = Arrays.stream(addresses[i])
                    .map(ad -> new SlotCopyContent(null, null, ad))
                    .toList();
            scds.get(i).setContents(contents);
        }

        when(mockCleanedOutSlotService.findCleanedOutSlots(any())).thenReturn(Set.of(
                lws[0].getSlot(A1), lws[0].getSlot(A2),
                lws[1].getSlot(A1), lws[1].getSlot(A2),
                lws[2].getSlot(A1)
        ));

        List<String> problems = new ArrayList<>(2);
        SlotCopyRequest request = new SlotCopyRequest();
        request.setDestinations(scds);
        service.checkCleanedOutDestinations(problems, request, lwMap);
        assertThat(problems).containsExactlyInAnyOrder(
                "Cannot add samples to cleaned out slots in labware STAN-0: [A1, A2]",
                "Cannot add samples to cleaned out slots in labware STAN-1: [A1]"
        );

        verify(mockCleanedOutSlotService).findCleanedOutSlots(lwMap.values());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidateOps(boolean cyt) {
        LabwareType lt = EntityFactory.getTubeType();
        UCMap<LabwareType> lts = UCMap.from(LabwareType::getName, lt);
        List<SlotCopyDestination> scds = List.of(
                makeSCD(null, lt.getName(), List.of(new SlotCopyContent("SRC", null, null))),
                makeSCD(null, lt.getName(), List.of(new SlotCopyContent("SRC2", null, null)))
        );
        OperationType opType = EntityFactory.makeOperationType(cyt ? "CytAssist" : "Bananas", null);
        doNothing().when(service).validateCytOp(any(), any(), any());
        List<String> problems = new ArrayList<>();
        service.validateOps(problems, scds, opType, lts);
        if (cyt) {
            verify(service, times(scds.size())).validateCytOp(any(), any(), any());
            scds.forEach(scd -> verify(service).validateCytOp(problems, scd.getContents(), lt));
        } else {
            verify(service, never()).validateCytOp(any(), any(), any());
        }
    }

    @Test
    public void testValidateCytOp_nolwtype() {
        List<String> problems = new ArrayList<>(0);
        service.validateCytOp(problems, List.of(), null);
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            ", Visium LP CytAssist, 1 4",
            ", Visium LP CytAssist XL, 1 2",
            ", Visium LP CytAssist HD, 1 2",
            "Expected a CytAssist labware type for operation CytAssist., Bananas, 1",
            "Slots B1 and C1 are disallowed for use in this operation., Visium LP CytAssist, 1 2",
            "Slots B1 and C1 are disallowed for use in this operation., Visium LP CytAssist, 3 4",
    })
    public void testValidateCytOp(String expectedProblem, String lwTypeName, String joinedRows) {
        List<SlotCopyContent> contents = Arrays.stream(joinedRows.split("\\s+"))
                .map(s  -> new SlotCopyContent(null, null, new Address(Integer.parseInt(s), 1)))
                .toList();
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.validateCytOp(problems, contents, EntityFactory.makeLabwareType(1,1, lwTypeName));
        assertProblem(problems, expectedProblem);
    }

    @ParameterizedTest
    @CsvSource({
            ", Probes/cDNA/Library, Probes/cDNA/Library",
            ",,",
            "Unknown bio state: [Bananas], Probes/Library/Bananas, Probes/Library",
            "Bio state not allowed for this operation: [Bananas], Probes/Library/Bananas, Probes/Library/Bananas",
    })
    public void testValidateBioStates(String joinedProblems, String joinedBsNames, String joinedRealBsNames) {
        List<String> expectedProblems = splitArg(joinedProblems);
        List<String> bsNames = splitArg(joinedBsNames);
        List<SlotCopyDestination> scds = bsNames.stream().map(bsname -> {
            SlotCopyDestination scd = new SlotCopyDestination();
            scd.setBioState(bsname);
            return scd;
        }).toList();
        List<BioState> bss;
        if (nullOrEmpty(joinedRealBsNames)) {
            bss = List.of();
        } else {
            final int[] idCounter = {0};
            bss = Arrays.stream(joinedRealBsNames.split("/"))
                    .map(name -> new BioState(++idCounter[0], name))
                    .toList();
        }
        final UCMap<BioState> expectedMap = UCMap.from(bss, BioState::getName);
        when(mockBsRepo.findAllByNameIn(any())).then(
                invocation -> invocation.<Collection<String>>getArgument(0).stream()
                        .map(expectedMap::get)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );

        List<String> problems = new ArrayList<>(expectedProblems.size());
        assertEquals(expectedMap, service.validateBioStates(problems, scds));
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static List<String> splitArg(String arg) {
        if (nullOrEmpty(arg)) {
            return List.of();
        }
        return Arrays.asList(arg.split("/"));
    }

    static SlotCopyDestination makeSCD(String barcode, String ltName, String bsName) {
        SlotCopyDestination scd = new SlotCopyDestination();
        scd.setBarcode(barcode);
        scd.setLabwareType(ltName);
        scd.setBioState(bsName);
        return scd;
    }

    @Test
    public void testCheckExistingDestinations() {
        // 1: no barcode
        // 2: no matching lw
        // 3: correct lw type and bs
        // 4: no specified lw type and bs
        // 5: wrong lw type
        // 6: wrong bs

        BioState bs1 = new BioState(1, "bs1");
        BioState bs2 = new BioState(2, "bs2");

        UCMap<BioState> bsMap = UCMap.from(BioState::getName, bs1, bs2);

        LabwareType lt1 = EntityFactory.makeLabwareType(1,1,"lt1");

        Sample sam1 = new Sample(1, null, EntityFactory.getTissue(), bs1);

        Labware[] lws = IntStream.rangeClosed(1, 4)
                .mapToObj(i -> {
                    Labware lw = EntityFactory.makeLabware(lt1, sam1);
                    lw.setBarcode("STAN-"+i);
                    return lw;
                })
                .toArray(Labware[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);

        List<SlotCopyDestination> scds = List.of(
                makeSCD(null, null, "bs1"),
                makeSCD("STAN-X", null, "bs1"),
                makeSCD("STAN-1", "lt1", "bs1"),
                makeSCD("STAN-2", null, ""),
                makeSCD("STAN-3", "lt2", "bs1"),
                makeSCD("STAN-4", "lt1", "bs2")
        );
        List<String> problems = new ArrayList<>(2);
        service.checkExistingDestinations(problems, scds, lwMap, bsMap);
        for (int i = 0; i < lws.length; ++i) {
            verify(service).checkExistingLabwareBioState(problems, lws[i], i==1 ? null : i==3 ? bs2 : bs1);
        }
        assertThat(problems).containsExactlyInAnyOrder(
                "Labware type lt2 specified for labware STAN-3 but it has type lt1.",
                "Bio state bs2 specified for labware STAN-4, which already uses bio state bs1."
        );
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";alpha/beta;alpha",
            ";alpha/beta;gamma",
            ";alpha;alpha",
            "Bio state Beta specified for labware STAN-1, which already uses bio state Alpha.; Alpha; Beta",
    }, delimiterString = ";")
    public void testCheckExistingLabwareBioState(String expectedProblem, String joinedOldBsNames, String newBsName) {
        List<String> oldBsNames = splitArg(joinedOldBsNames);
        UCMap<BioState> bsMap = new UCMap<>(oldBsNames.size()+1);
        final int[] idCounter = {0};
        for (String oldBsName : oldBsNames) {
            if (bsMap.get(oldBsName)==null) {
                BioState bs = new BioState(++idCounter[0], oldBsName);
                bsMap.put(oldBsName, bs);
            }
        }
        BioState newBs = bsMap.get(newBsName);
        if (newBs==null) {
            newBs = new BioState(++idCounter[0], newBsName);
        }
        final Tissue tissue = EntityFactory.getTissue();
        Sample[] samples = oldBsNames.stream().map(bsMap::get)
                .map(bs -> new Sample(++idCounter[0], null, tissue, bs))
                .toArray(Sample[]::new);
        LabwareType lt = EntityFactory.makeLabwareType(1, samples.length, "lt");
        Labware lw = EntityFactory.makeLabware(lt, samples);
        lw.setBarcode("STAN-1");

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.checkExistingLabwareBioState(problems, lw, newBs);
        assertProblem(problems, expectedProblem);
    }
}