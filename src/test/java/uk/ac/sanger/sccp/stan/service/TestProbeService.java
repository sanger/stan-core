package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.ProbePanel.ProbeType;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ProbeOperationRequest;
import uk.ac.sanger.sccp.stan.request.ProbeOperationRequest.ProbeLot;
import uk.ac.sanger.sccp.stan.request.ProbeOperationRequest.ProbeOperationLabware;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;
import uk.ac.sanger.sccp.utils.Zip;

import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.stan.service.ProbeServiceImp.REAGENT_LOT_NAME;
import static uk.ac.sanger.sccp.utils.BasicUtils.concat;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;


/**
 * Tests {@link ProbeServiceImp}.
 */
public class TestProbeService {
    @Mock
    private LabwareValidatorFactory mockLwValFac;
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private OperationRepo mockOpRepo;
    @Mock
    private ProbePanelRepo mockProbePanelRepo;
    @Mock
    private LabwareProbeRepo mockLwProbeRepo;
    @Mock
    private LabwareNoteRepo mockNoteRepo;
    @Mock
    private OperationService mockOpService;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private Validator<String> mockProbeLotValidator;
    @Mock
    private Validator<String> mockReagentLotValidator;
    @Mock
    private Clock mockClock;

    @InjectMocks
    private ProbeServiceImp service;

    private AutoCloseable mocking;


    @BeforeEach
    void setUp() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(new ProbeServiceImp(mockLwValFac, mockLwRepo, mockOpTypeRepo, mockOpRepo,
                mockProbePanelRepo, mockLwProbeRepo, mockNoteRepo, mockOpService, mockWorkService,
                mockProbeLotValidator, mockReagentLotValidator, mockClock));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @MethodSource("recordProbeOperationArgs")
    public void testRecordProbeOperation(User user, ProbeOperationRequest request,
                                         List<String> expectedProblems) {
        Labware lw = EntityFactory.getTube();
        ProbeType probeType = ProbeType.xenium;
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        doReturn(lwMap).when(service).validateLabware(any(), any());
        OperationType opType = EntityFactory.makeOperationType("Alibobs", null);
        doNothing().when(service).checkAllAddresses(any(), any(), any());
        doReturn(opType).when(service).validateOpType(any(), any());
        doReturn(probeType).when(service).opProbeType(opType);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        doReturn(workMap).when(mockWorkService).validateUsableWorks(any(), any());
        UCMap<ProbePanel> ppMap = UCMap.from(ProbePanel::getName, new ProbePanel(probeType, "bananas"));
        doReturn(ppMap).when(service).validateProbes(any(), any(), any());
        UCMap<ProbePanel> spikeMap = UCMap.from(ProbePanel::getName, new ProbePanel(ProbeType.spike, "william"));
        doReturn(spikeMap).when(service).checkSpikes(any(), any());
        doNothing().when(service).validateTimestamp(any(), any(), any());
        doNothing().when(service).checkReagentLots(any(), any());
        doNothing().when(service).checkKitCostings(any(), any());

        if (nullOrEmpty(expectedProblems)) {
            OperationResult opres = new OperationResult(List.of(), List.of(lw));
            doReturn(opres).when(service).perform(any(), any(), any(), any(), any(), any(), any(), any());
            assertSame(opres, service.recordProbeOperation(user, request));
            verify(service).perform(user, request.getLabware(), opType, request.getPerformed(), lwMap, ppMap, spikeMap, workMap);
        } else {
            assertValidationException(() -> service.recordProbeOperation(user, request), expectedProblems);
        }
        if (request==null || nullOrEmpty(request.getLabware())) {
            verify(service, never()).validateLabware(any(), any());
            verify(service, never()).validateOpType(any(), any());
            verifyNoInteractions(mockWorkService);
            verify(service, never()).validateProbes(any(), any(), any());
            verify(service, never()).validateTimestamp(any(), any(), any());
            verify(service, never()).checkReagentLots(any(), any());
            verify(service, never()).checkKitCostings(any(), any());
            return;
        }
        final List<ProbeOperationLabware> pols = request.getLabware();

        ArgumentCaptor<Stream<String>> captor = Matchers.streamCaptor();
        verify(service).validateLabware(any(), captor.capture());
        List<String> lwBarcodes = pols.stream()
                .map(ProbeOperationLabware::getBarcode)
                .collect(toList());
        assertThat(captor.getValue()).containsExactlyElementsOf(lwBarcodes);
        verify(service).validateOpType(any(), eq(request.getOperationType()));
        verify(mockWorkService).validateUsableWorks(any(), eq(pols.stream()
                .map(ProbeOperationLabware::getWorkNumber)
                .collect(toList())));
        verify(service).checkAllAddresses(any(), any(), any());
        verify(service).opProbeType(opType);
        verify(service).validateProbes(any(), same(probeType), eq(pols));
        verify(service).checkSpikes(any(), same(pols));
        verify(service).checkReagentLots(any(), eq(pols));
        verify(service).checkKitCostings(any(), eq(pols));
        if (request.getPerformed()==null) {
            verify(service, never()).validateTimestamp(any(), any(), any());
        } else {
            verify(service).validateTimestamp(any(), same(request.getPerformed()), same(lwMap));
        }
    }

    @Test
    public void testRecordProbeOperationProblems() {
        ProbeType probeType = ProbeType.xenium;
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        doAnswer(addProblem("Bad labware", lwMap)).when(service).validateLabware(any(), any());
        OperationType opType = EntityFactory.makeOperationType("optype", null);
        doAnswer(addProblem("Bad op type", opType)).when(service).validateOpType(any(), any());
        doReturn(probeType).when(service).opProbeType(opType);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        doAnswer(addProblem("Bad work", workMap)).when(mockWorkService).validateUsableWorks(any(), any());
        UCMap<ProbePanel> ppMap = UCMap.from(ProbePanel::getName, new ProbePanel(probeType, "pp1"));
        doAnswer(addProblem("Bad probe", ppMap)).when(service).validateProbes(any(), any(), any());
        doAnswer(addProblem("Bad time")).when(service).validateTimestamp(any(), any(), any());
        doAnswer(addProblem("Bad costing")).when(service).checkKitCostings(any(), any());
        User user = EntityFactory.getUser();
        ProbeOperationRequest request = new ProbeOperationRequest("optype", LocalDateTime.now(),
                List.of(new ProbeOperationLabware("Alpha", "Beta", SlideCosting.SGP, null, List.of(), null)));
        Matchers.assertValidationException(() -> service.recordProbeOperation(user, request),
                List.of("Bad labware", "Bad op type", "Bad work", "Bad probe", "Bad time", "Bad costing"));
        ArgumentCaptor<Stream<String>> streamCaptor = Matchers.streamCaptor();
        verify(service).validateLabware(any(), streamCaptor.capture());
        assertThat(streamCaptor.getValue()).containsExactly("Alpha");
        verify(service).validateOpType(any(), eq("optype"));
        verify(mockWorkService).validateUsableWorks(any(), eq(List.of("Beta")));
        verify(service).validateProbes(any(), same(probeType), same(request.getLabware()));
        verify(service).checkSpikes(any(), same(request.getLabware()));
        verify(service).validateTimestamp(any(), same(request.getPerformed()), same(lwMap));
        verify(service).checkKitCostings(any(), same(request.getLabware()));
        verify(service, never()).perform(any(), any(), any(), any(), any(), any(), any(), any());
    }

    static Stream<Arguments> recordProbeOperationArgs() {
        User user = EntityFactory.getUser();
        ProbeOperationRequest emptyRequest = new ProbeOperationRequest();
        ProbeOperationLabware pol = new ProbeOperationLabware("STAN-A1", "SGP-1", SlideCosting.SGP, null, List.of(), null);
        LocalDateTime time = LocalDateTime.now();
        ProbeOperationRequest completeRequest = new ProbeOperationRequest("optype", time, List.of(pol));
        return Arrays.stream(new Object[][] {
                {null, null, List.of("No user supplied.", "No request supplied.")},
                {user, null, List.of("No request supplied.")},
                {user, emptyRequest, List.of("No labware specified.")},
                {user, completeRequest, List.of()},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,false", "2,false", "0,true", "1,true"})
    public void testValidateLabware(int numMissing, boolean anyValErrors) {
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFac.getValidator()).thenReturn(val);
        List<String> valErrors = anyValErrors ? List.of("Error1", "Error2") : List.of();
        when(val.getErrors()).thenReturn(valErrors);
        Labware lw1 = EntityFactory.getTube();
        when(val.getLabware()).thenReturn(List.of(lw1));

        Stream<String> bcStream = Stream.of(numMissing>1 ? null : "bc1", numMissing>0 ? null : "bc2");

        List<String> expectedErrors = valErrors;
        if (numMissing > 0) {
            expectedErrors = concat(valErrors, List.of(numMissing==1 ? "Labware barcode missing." : "No labware barcodes supplied."));
        }
        final List<String> problems = new ArrayList<>(expectedErrors.size());
        UCMap<Labware> result = service.validateLabware(problems, bcStream);

        if (numMissing < 2) {
            assertThat(result).hasSize(1);
            assertSame(result.get(lw1.getBarcode()), lw1);
        } else {
            assertThat(result).isEmpty();
        }

        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedErrors);
        if (numMissing==2) {
            verifyNoInteractions(mockLwValFac);
            verifyNoInteractions(val);
        } else {
            verify(val).loadLabware(mockLwRepo, numMissing==0 ? List.of("bc1", "bc2") : List.of("bc1"));
            verify(val).validateSources();
        }
    }

    @Test
    public void testCheckAllAddresses() {
        List<String> problems = new ArrayList<>();
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> lwList = IntStream.range(0, 2).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).toList();
        UCMap<Labware> lwMap = UCMap.from(lwList, Labware::getBarcode);
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware(lwList.get(0).getBarcode(), null, null, null, null, null),
                new ProbeOperationLabware(lwList.get(1).getBarcode(), null, null, null, null, null),
                new ProbeOperationLabware("NO SUCH LW", null, null, null, null, null)
        );
        final Address A1 = new Address(1,1), A2 = new Address(2,2);
        List<Address> addresses = List.of(A1, A2);
        pols.get(0).setAddresses(addresses);
        pols.get(2).setAddresses(List.of(A1));
        doNothing().when(service).checkAddresses(any(), any(), any());

        service.checkAllAddresses(problems, pols, lwMap);
        verify(service, times(1)).checkAddresses(any(), any(), any());
        verify(service).checkAddresses(same(problems), same(addresses), same(lwList.get(0)));
    }

    @ParameterizedTest
    @CsvSource({
            "false,false,false",
            "true,false,false",
            "false,true,false",
            "false,false,true",
            "true,true,true",
    })
    public void testCheckAddresses(boolean addressNull, boolean empty, boolean invalid) {
        List<Address> addresses = new ArrayList<>();
        final Address A1 = new Address(1,1), A2 = new Address(1,2),  A3 = new Address(1,3);
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw = EntityFactory.makeLabware(lt, sample);
        lw.setBarcode("STAN-1");
        addresses.add(A1);
        List<String> expectedProblems = new ArrayList<>();
        if (addressNull) {
            addresses.add(null);
            expectedProblems.add("Null address given for labware STAN-1.");
        }
        if (empty) {
            addresses.add(A2);
            expectedProblems.add("Slot contains no samples in labware STAN-1: [A2]");
        }
        if (invalid) {
            addresses.add(A3);
            expectedProblems.add("Slot not present in labware STAN-1: [A3]");
        }
        List<String> problems = new ArrayList<>();
        service.checkAddresses(problems, addresses, lw);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    @ParameterizedTest
    @CsvSource({
            "opname, true, true, true,",
            ",false,false,false,No operation type specified.",
            "'',false,false,false,No operation type specified.",
            "opname,false,false,false,Unknown operation type: \"opname\"",
            "opname,true,true,false,Operation type opname cannot be used in this request.",
            "opname,true,false,true,Operation type opname cannot be used in this request.",
    })
    public void testValidateOpType(String opName, boolean exists, boolean inplace, boolean probular, String expectedProblem) {
        OperationType opType;
        if (exists) {
            OperationTypeFlag[] flags;
            if (inplace && probular) {
                flags = new OperationTypeFlag[] { OperationTypeFlag.IN_PLACE, OperationTypeFlag.PROBES };
            } else if (inplace || probular) {
                flags = new OperationTypeFlag[] { (inplace ? OperationTypeFlag.IN_PLACE : OperationTypeFlag.PROBES) };
            } else {
                flags = new OperationTypeFlag[] {};
            }
            opType = EntityFactory.makeOperationType(opName, null, flags);
        } else {
            opType = null;
        }
        when(mockOpTypeRepo.findByName(opName)).thenReturn(Optional.ofNullable(opType));

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(opType, service.validateOpType(problems, opName));
        assertProblem(problems, expectedProblem);
    }

    @Test
    public void testCheckReagentLots() {
        when(mockReagentLotValidator.validate(any(), any())).then(invocation -> {
            String string = invocation.getArgument(0);
            if (string.indexOf('!') < 0) {
                return true;
            }
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("Bad lot: " + string);
            return false;
        });
        String[] lots = {null, "", "   ", "  Good  ", "Bad!"};
        String[] sanitisedLots = {null, null, null, "Good", "Bad!"};
        List<ProbeOperationLabware> pols = Arrays.stream(lots)
                .map(lot -> new ProbeOperationLabware("bc", null, null, lot, null, null))
                .toList();
        List<String> problems = new ArrayList<>(1);
        service.checkReagentLots(problems, pols);
        assertProblem(problems, "Bad lot: Bad!");
        for (int i = 0; i < pols.size(); i++) {
            ProbeOperationLabware pol = pols.get(i);
            String lot = sanitisedLots[i];
            assertEquals(lot, pol.getReagentLot());
            if (lot!=null) {
                verify(mockReagentLotValidator).validate(eq(lot), any());
            }
        }
        verify(mockReagentLotValidator, never()).validate(isNull(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCheckKitCostings(boolean anyMissing) {
        List<SlideCosting> costings;
        String expectedProblem;
        if (anyMissing) {
            costings = Arrays.asList(SlideCosting.SGP, SlideCosting.Faculty, null, SlideCosting.SGP, null);
            expectedProblem = "Missing kit costing for labware.";
        } else {
            costings = List.of(SlideCosting.SGP, SlideCosting.Faculty, SlideCosting.SGP);
            expectedProblem = null;
        }
        List<ProbeOperationLabware> pols = costings.stream()
                .map(costing -> new ProbeOperationLabware(null, null, costing, null, null, null))
                .toList();
        List<String> problems = new ArrayList<>(anyMissing ? 1 : 0);
        service.checkKitCostings(problems, pols);
        assertProblem(problems, expectedProblem);
    }

    @Test
    public void testValidateProbes() {
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("BC1", "SGP1", SlideCosting.SGP,
                        null, List.of(new ProbeLot("p1", "lot1", 1, SlideCosting.SGP),
                                new ProbeLot("p2", "lot2", 2, SlideCosting.SGP)), null),
                new ProbeOperationLabware("BC2", "SGP2", SlideCosting.Faculty,
                        null, List.of(new ProbeLot("p2", "lot3", 3, SlideCosting.Faculty),
                                new ProbeLot("p3", "lot4", 4, SlideCosting.SGP)), null)
        );
        final ProbeType probeType = ProbeType.xenium;
        List<ProbePanel> probes = IntStream.range(1, 4)
                .mapToObj(i -> new ProbePanel(i, probeType, "p"+i))
                .collect(toList());
        when(mockProbePanelRepo.findAllByTypeAndNameIn(same(probeType), any())).thenReturn(probes);
        final List<String> problems = new ArrayList<>();
        UCMap<ProbePanel> ppMap = service.validateProbes(problems, probeType, pols);
        assertThat(problems).isEmpty();
        assertThat(ppMap).hasSize(probes.size());
        probes.forEach(pp -> assertSame(pp, ppMap.get(pp.getName())));

        IntStream.range(1,4).forEach(i -> verify(mockProbeLotValidator).validate(eq("lot"+i), any()));
        verify(mockProbePanelRepo).findAllByTypeAndNameIn(probeType, Set.of("p1", "p2", "p3"));
    }

    @Test
    public void testValidateNoProbes() {
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("BC1", "SGP1", SlideCosting.SGP,
                        null, List.of(new ProbeLot("p1", "lot1", 1, SlideCosting.Faculty)), null),
                new ProbeOperationLabware("BC2", "SGP2", SlideCosting.Faculty,
                        null, List.of(), null)
        );
        ProbeType probeType = ProbeType.xenium;
        List<ProbePanel> probes = List.of(new ProbePanel(1, probeType, "p1"));
        when(mockProbePanelRepo.findAllByTypeAndNameIn(any(), any())).thenReturn(probes);
        final List<String> problems = new ArrayList<>();
        UCMap<ProbePanel> ppMap = service.validateProbes(problems, probeType, pols);
        assertThat(problems).containsExactly("No probes specified for labware.");
        assertThat(ppMap).hasSize(probes.size());
        probes.forEach(pp -> assertSame(pp, ppMap.get(pp.getName())));
        verify(mockProbeLotValidator).validate(eq("lot1"), any());
        verify(mockProbePanelRepo).findAllByTypeAndNameIn(probeType, Set.of("p1"));
    }

    @ParameterizedTest
    @CsvSource({"p1, lot2, 3, SGP,",
            ", lot2, 3, Faculty, Probe panel name missing.",
            "p!, lot2, 3, Faculty, Unknown xenium probe panels: [\"p!\"]",
            "p1, , 3, Faculty,",
            "p1, lot!, 3, SGP, Bad lot.",
            "p1, lot2,,SGP,",
            "p1, lot2, 0, SGP, Probe plex should be a positive number.",
            "p1, lot2, 2, , Probe costing is missing.",
    })
    public void testValidateProbes_problems(String probeName, String lot, Integer plex, SlideCosting costing, String expectedProblem) {
        ProbeOperationLabware pol = new ProbeOperationLabware("BC", "SGP1", SlideCosting.SGP,
                null, List.of(new ProbeLot(probeName, lot, plex, costing)), null);
        ProbeType probeType = ProbeType.xenium;
        when(mockProbeLotValidator.validate(any(), any())).then(invocation -> {
            String lotArg = invocation.getArgument(0);
            if (lotArg.indexOf('!')<0) {
                return true;
            }
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("Bad lot.");
            return false;
        });
        List<String> problems = new ArrayList<>(1);
        ProbePanel pp;
        if (probeName!=null && probeName.indexOf('!') < 0) {
            pp = new ProbePanel(probeType, probeName);
            when(mockProbePanelRepo.findAllByTypeAndNameIn(any(), any())).thenReturn(List.of(pp));
        } else {
            pp = null;
            when(mockProbePanelRepo.findAllByTypeAndNameIn(any(), any())).thenReturn(List.of());
        }

        UCMap<ProbePanel> ppMap = service.validateProbes(problems, probeType, List.of(pol));
        assertProblem(problems, expectedProblem);
        if (pp==null) {
            assertThat(ppMap).isEmpty();
        } else {
            assertThat(ppMap).hasSize(1);
            assertSame(pp, ppMap.get(pp.getName()));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1, 2, 3,",
            "2, 1, 3, The given date is too early to be valid for the specified labware.",
            "1, 3, 2, The given date is in the future.",
    })
    public void testValidateTimestamp(int lwCreated, int performedDay, int today, String expectedProblem) {
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        lw.setCreated(LocalDateTime.of(2023, 1, lwCreated, 12, 0));
        LocalDateTime performed = LocalDateTime.of(2023,1, performedDay, 13, 0);
        LocalDateTime now = LocalDateTime.of(2023, 1, today, 14, 0);
        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        when(mockClock.getZone()).thenReturn(ZoneId.systemDefault());
        when(mockClock.instant()).thenReturn(now.toInstant(ZoneId.systemDefault().getRules().getOffset(now)));
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);

        service.validateTimestamp(problems, performed, lwMap);
        assertProblem(problems, expectedProblem);
    }

    @Test
    public void testPerform() {
        User user = EntityFactory.getUser();
        final ProbeType probeType = ProbeType.xenium;
        List<ProbeOperationLabware> pols = List.of(new ProbeOperationLabware());
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        LocalDateTime time = LocalDateTime.now();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        UCMap<ProbePanel> ppMap = UCMap.from(ProbePanel::getName, new ProbePanel(probeType, "banana"));
        UCMap<ProbePanel> spikeMap = UCMap.from(ProbePanel::getName, new ProbePanel(ProbeType.spike, "william"));
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        UCMap<Operation> lwOps = new UCMap<>();
        lwOps.put("STAN1", new Operation());
        doReturn(lwOps).when(service).makeOps(any(), any(), any(), any(), any());
        doNothing().when(service).linkWork(any(), any(), any());
        doNothing().when(service).saveProbes(any(), any(), any(), any(), any());
        doNothing().when(service).saveReagentLots(any(), any(), any());
        doNothing().when(service).saveKitCostings(any(), any(), any());
        OperationResult opres = new OperationResult(List.of(), List.of());
        doReturn(opres).when(service).assembleResult(any(), any(), any());

        assertSame(opres, service.perform(user, pols, opType, time, lwMap, ppMap, spikeMap, workMap));
        verify(service).makeOps(user, opType, pols, lwMap, time);
        verify(service).linkWork(pols, lwOps, workMap);
        verify(service).saveProbes(pols, lwOps, lwMap, ppMap, spikeMap);
        verify(service).saveReagentLots(pols, lwMap, lwOps);
        verify(service).saveKitCostings(pols, lwMap, lwOps);
        verify(service).assembleResult(pols, lwMap, lwOps);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testMakeOps(boolean hasTime) {
        User user = EntityFactory.getUser();
        OperationType optype = EntityFactory.makeOperationType("opname", null);
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("STAN-1", null, SlideCosting.SGP, null, null, null),
                new ProbeOperationLabware("STAN-2", null, SlideCosting.Faculty, null, null, null)
        );
        final List<Address> addresses = List.of(new Address(1, 2), new Address(3, 4));
        pols.getFirst().setAddresses(addresses);
        LabwareType lt = EntityFactory.getTubeType();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.makeEmptyLabware(lt, "STAN-1"),
                EntityFactory.makeEmptyLabware(lt, "STAN-2"));
        LocalDateTime time = (hasTime ? LocalDateTime.now() : null);
        Operation[] ops = { new Operation(), new Operation() };
        ops[0].setId(1);
        ops[1].setId(2);
        doReturn(ops[0], ops[1]).when(service).createOp(any(), any(), any(), any());

        UCMap<Operation> opMap = service.makeOps(user, optype, pols, lwMap, time);
        verify(service).createOp(optype, user, lwMap.get("STAN-1"), addresses);
        verify(service).createOp(optype, user, lwMap.get("STAN-2"), List.of());

        assertThat(opMap).hasSize(2);
        assertSame(ops[0], opMap.get("STAN-1"));
        assertSame(ops[1], opMap.get("STAN-2"));
        for (Operation op : ops) {
            assertEquals(time, op.getPerformed());
        }
        if (time!=null) {
            verify(mockOpRepo).saveAll(opMap.values());
        } else {
            verifyNoInteractions(mockOpRepo);
        }
    }

    @Test
    public void testCreateOp_noAddresses() {
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        Operation op = new Operation();
        op.setId(1);
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);
        assertSame(op, service.createOp(opType, user, lw, List.of()));
        verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
    }

    @Test
    public void testCreateOp_addresses() {
        final Address A1 = new Address(1, 1), A2 = new Address(1,2), A3 = new Address(1,3);
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        User user = EntityFactory.getUser();
        List<Address> addresses = List.of(A1, A3);
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        Sample[] samples = EntityFactory.makeSamples(3);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.getSlot(A1).setSamples(List.of(samples[0], samples[1]));
        lw.getSlot(A2).setSamples(List.of(samples[2]));
        lw.getSlot(A3).setSamples(List.of(samples[0]));
        List<Slot> slots = lw.getSlots();
        Operation op = new Operation();
        op.setId(1);
        when(mockOpService.createOperation(any(), any(), any(), any())).thenReturn(op);

        assertSame(op, service.createOp(opType, user, lw, addresses));
        List<Action> expectedActions = List.of(
                new Action(null, null, slots.get(0), slots.get(0), samples[0], samples[0]),
                new Action(null, null, slots.get(0), slots.get(0), samples[1], samples[1]),
                new Action(null, null, slots.get(2), slots.get(2), samples[0], samples[0])
        );
        verify(mockOpService).createOperation(opType, user, expectedActions, null);
    }

    @Test
    public void testLinkWork() {
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("STAN-1", "SGP1", SlideCosting.SGP, null, null, null),
                new ProbeOperationLabware("STAN-2", "SGP1", SlideCosting.SGP, null, null, null),
                new ProbeOperationLabware("STAN-3", "SGP2", SlideCosting.Faculty, null, null, null)
        );
        Operation[] ops = IntStream.range(1, 4)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(i);
                    return op;
                }).toArray(Operation[]::new);
        UCMap<Operation> lwOps = new UCMap<>(3);
        for (int i = 0; i < ops.length; ++i) {
            lwOps.put("STAN-"+(i+1), ops[i]);
        }
        Work[] works = IntStream.range(1,3)
                .mapToObj(i -> EntityFactory.makeWork("SGP"+i))
                .toArray(Work[]::new);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, works);

        service.linkWork(pols, lwOps, workMap);

        verify(mockWorkService).link(works[0], List.of(ops[0], ops[1]));
        verify(mockWorkService).link(works[1], List.of(ops[2]));
    }

    @Test
    public void testSaveKitCostings() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        Operation op1 = new Operation();
        op1.setId(501);
        Operation op2 = new Operation();
        op2.setId(502);
        UCMap<Operation> opMap = new UCMap<>(2);
        opMap.put(lw1.getBarcode(), op1);
        opMap.put(lw2.getBarcode(), op2);
        final String noteName = ProbeServiceImp.KIT_COSTING_NAME;

        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware(lw1.getBarcode(), null, SlideCosting.SGP, null, null, null),
                new ProbeOperationLabware(lw2.getBarcode(), null, SlideCosting.Faculty, null, null, null)
        );

        service.saveKitCostings(pols, lwMap, opMap);

        List<LabwareNote> expectedNotes = List.of(
                new LabwareNote(null, lw1.getId(), op1.getId(), noteName, "SGP"),
                new LabwareNote(null, lw2.getId(), op2.getId(), noteName, "Faculty")
        );
        verify(mockNoteRepo).saveAll(expectedNotes);
    }

    @Test
    public void testSaveProbes() {
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("STAN-1", null, SlideCosting.Faculty, null, List.of(
                        new ProbeLot("probe1", "lot1", 1, SlideCosting.SGP),
                        new ProbeLot("probe2", "lot2", 2, SlideCosting.SGP)
                ), null),
                new ProbeOperationLabware("STAN-2", null, SlideCosting.SGP, null, List.of(
                        new ProbeLot("probe1", "lot3", 3, SlideCosting.Faculty)
                ), "william")
        );
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt, "STAN-1");
        Labware lw2 = EntityFactory.makeEmptyLabware(lt, "STAN-2");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        ProbeType probeType = ProbeType.xenium;
        ProbePanel pp1 = new ProbePanel(probeType, "probe1");
        ProbePanel pp2 = new ProbePanel(probeType, "probe2");
        ProbePanel spike = new ProbePanel(ProbeType.spike, "william");
        UCMap<ProbePanel> ppMap = UCMap.from(ProbePanel::getName, pp1, pp2);
        UCMap<ProbePanel> spikeMap = UCMap.from(ProbePanel::getName, spike);

        Operation op1 = new Operation();
        Operation op2 = new Operation();
        op1.setId(1);
        op2.setId(2);
        UCMap<Operation> lwOps = new UCMap<>(2);
        lwOps.put("STAN-1", op1);
        lwOps.put("STAN-2", op2);

        service.saveProbes(pols, lwOps, lwMap, ppMap, spikeMap);

        verify(mockLwProbeRepo).saveAll(List.of(
                new LabwareProbe(null, pp1, op1.getId(), lw1.getId(), "LOT1", 1, SlideCosting.SGP),
                new LabwareProbe(null, pp2, op1.getId(), lw1.getId(), "LOT2", 2, SlideCosting.SGP),
                new LabwareProbe(null, pp1, op2.getId(), lw2.getId(), "LOT3", 3, SlideCosting.Faculty),
                new LabwareProbe(null, spike, op2.getId(), lw2.getId(), null, null, null)
        ));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testSaveReagentLots(boolean anyLots) {
        String[] lots = anyLots ? new String[] {null, "Alpha", "Beta", null, "Alpha"} : new String[] { null, null };
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = Arrays.stream(lots).map(unused -> EntityFactory.makeEmptyLabware(lt)).toArray(Labware[]::new);
        Operation[] ops = Arrays.stream(lots).map(unused -> new Operation()).toArray(Operation[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        UCMap<Operation> opMap = new UCMap<>(ops.length);
        Zip.enumerate(Arrays.stream(ops)).forEach((i, op) -> {
            op.setId(100+i);
            opMap.put(lws[i].getBarcode(), op);
        });

        List<ProbeOperationLabware> pols = Zip.of(Arrays.stream(lws), Arrays.stream(lots))
                .map((lw, lot) -> new ProbeOperationLabware(lw.getBarcode(), null, null, lot, null, null)
        ).toList();
        service.saveReagentLots(pols, lwMap, opMap);
        if (anyLots) {
            List<LabwareNote> expectedNotes = IntStream.range(0, lots.length)
                    .filter(i -> lots[i] != null)
                    .mapToObj(i -> new LabwareNote(null, lws[i].getId(), ops[i].getId(), REAGENT_LOT_NAME, lots[i]))
                    .toList();
            verify(mockNoteRepo).saveAll(expectedNotes);
        } else {
            verifyNoInteractions(mockNoteRepo);
        }
    }

    @Test
    public void testAssembleResult() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = { EntityFactory.makeEmptyLabware(lt, "STAN-1"), EntityFactory.makeEmptyLabware(lt, "STAN-2")};
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, labware);
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("STAN-1", null, SlideCosting.SGP, null, null, null),
                new ProbeOperationLabware("STAN-2", null, SlideCosting.Faculty, null, null, null)
        );
        Operation[] ops = { new Operation(), new Operation()};
        ops[0].setId(1);
        ops[1].setId(2);
        UCMap<Operation> lwOps = new UCMap<>(2);
        lwOps.put("STAN-1", ops[0]);
        lwOps.put("STAN-2", ops[1]);

        OperationResult opres = service.assembleResult(pols, lwMap, lwOps);

        assertThat(opres.getLabware()).containsExactly(labware);
        assertThat(opres.getOperations()).containsExactly(ops);
    }
}