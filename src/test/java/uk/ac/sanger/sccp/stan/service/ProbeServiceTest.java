package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ProbeOperationRequest;
import uk.ac.sanger.sccp.stan.request.ProbeOperationRequest.ProbeLot;
import uk.ac.sanger.sccp.stan.request.ProbeOperationRequest.ProbeOperationLabware;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

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
import static uk.ac.sanger.sccp.utils.BasicUtils.concat;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;


/**
 * Tests {@link ProbeServiceImp}.
 */
public class ProbeServiceTest {
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
    private OperationService mockOpService;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private Validator<String> mockProbeLotValidator;
    @Mock
    private Clock mockClock;

    @InjectMocks
    private ProbeServiceImp service;

    private AutoCloseable mocking;


    @BeforeEach
    void setUp() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
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
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        doReturn(lwMap).when(service).validateLabware(any(), any());
        OperationType opType = EntityFactory.makeOperationType("Alibobs", null);
        doReturn(opType).when(service).validateOpType(any(), any());
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        doReturn(workMap).when(mockWorkService).validateUsableWorks(any(), any());
        UCMap<ProbePanel> ppMap = UCMap.from(ProbePanel::getName, new ProbePanel("bananas"));
        doReturn(ppMap).when(service).validateProbes(any(), any());
        doNothing().when(service).validateTimestamp(any(), any(), any());

        if (nullOrEmpty(expectedProblems)) {
            OperationResult opres = new OperationResult(List.of(), List.of(lw));
            doReturn(opres).when(service).perform(any(), any(), any(), any(), any(), any(), any());
            assertSame(opres, service.recordProbeOperation(user, request));
            verify(service).perform(user, request.getLabware(), opType, request.getPerformed(), lwMap, ppMap, workMap);
        } else {
            assertValidationException(() -> service.recordProbeOperation(user, request), expectedProblems);
        }
        if (request==null || nullOrEmpty(request.getLabware())) {
            verify(service, never()).validateLabware(any(), any());
            verify(service, never()).validateOpType(any(), any());
            verifyNoInteractions(mockWorkService);
            verify(service, never()).validateProbes(any(), any());
            verify(service, never()).validateTimestamp(any(), any(), any());
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
        verify(service).validateProbes(any(), eq(pols));
        if (request.getPerformed()==null) {
            verify(service, never()).validateTimestamp(any(), any(), any());
        } else {
            verify(service).validateTimestamp(any(), same(request.getPerformed()), same(lwMap));
        }
    }

    @Test
    public void testRecordProbeOperationProblems() {
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        doAnswer(addProblem("Bad labware", lwMap)).when(service).validateLabware(any(), any());
        OperationType opType = EntityFactory.makeOperationType("optype", null);
        doAnswer(addProblem("Bad op type", opType)).when(service).validateOpType(any(), any());
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        doAnswer(addProblem("Bad work", workMap)).when(mockWorkService).validateUsableWorks(any(), any());
        UCMap<ProbePanel> ppMap = UCMap.from(ProbePanel::getName, new ProbePanel("pp1"));
        doAnswer(addProblem("Bad probe", ppMap)).when(service).validateProbes(any(), any());
        doAnswer(addProblem("Bad time")).when(service).validateTimestamp(any(), any(), any());
        User user = EntityFactory.getUser();
        ProbeOperationRequest request = new ProbeOperationRequest("optype", LocalDateTime.now(),
                List.of(new ProbeOperationLabware("Alpha", "Beta", List.of())));
        Matchers.assertValidationException(() -> service.recordProbeOperation(user, request),
                List.of("Bad labware", "Bad op type", "Bad work", "Bad probe", "Bad time"));
        ArgumentCaptor<Stream<String>> streamCaptor = Matchers.streamCaptor();
        verify(service).validateLabware(any(), streamCaptor.capture());
        assertThat(streamCaptor.getValue()).containsExactly("Alpha");
        verify(service).validateOpType(any(), eq("optype"));
        verify(mockWorkService).validateUsableWorks(any(), eq(List.of("Beta")));
        verify(service).validateProbes(any(), same(request.getLabware()));
        verify(service).validateTimestamp(any(), same(request.getPerformed()), same(lwMap));
        verify(service, never()).perform(any(), any(), any(), any(), any(), any(), any());
    }

    static Stream<Arguments> recordProbeOperationArgs() {
        User user = EntityFactory.getUser();
        ProbeOperationRequest emptyRequest = new ProbeOperationRequest();
        ProbeOperationLabware pol = new ProbeOperationLabware("STAN-A1", "SGP-1", List.of());
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
    public void testValidateProbes() {
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("BC1", "SGP1",
                        List.of(new ProbeLot("p1", "lot1", 1, SlideCosting.SGP),
                                new ProbeLot("p2", "lot2", 2, SlideCosting.SGP))),
                new ProbeOperationLabware("BC2", "SGP2",
                        List.of(new ProbeLot("p2", "lot3", 3, SlideCosting.Faculty),
                                new ProbeLot("p3", "lot4", 4, SlideCosting.SGP)))
        );
        List<ProbePanel> probes = IntStream.range(1, 4)
                .mapToObj(i -> new ProbePanel(i, "p"+i))
                .collect(toList());
        when(mockProbePanelRepo.findAllByNameIn(any())).thenReturn(probes);
        final List<String> problems = new ArrayList<>();
        UCMap<ProbePanel> ppMap = service.validateProbes(problems, pols);
        assertThat(problems).isEmpty();
        assertThat(ppMap).hasSize(probes.size());
        probes.forEach(pp -> assertSame(pp, ppMap.get(pp.getName())));

        IntStream.range(1,4).forEach(i -> verify(mockProbeLotValidator).validate(eq("lot"+i), any()));
        verify(mockProbePanelRepo).findAllByNameIn(Set.of("p1", "p2", "p3"));
    }

    @Test
    public void testValidateNoProbes() {
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("BC1", "SGP1",
                        List.of(new ProbeLot("p1", "lot1", 1, SlideCosting.Faculty))),
                new ProbeOperationLabware("BC2", "SGP2",
                        List.of())
        );
        List<ProbePanel> probes = List.of(new ProbePanel(1, "p1"));
        when(mockProbePanelRepo.findAllByNameIn(any())).thenReturn(probes);
        final List<String> problems = new ArrayList<>();
        UCMap<ProbePanel> ppMap = service.validateProbes(problems, pols);
        assertThat(problems).containsExactly("No probes specified for labware.");
        assertThat(ppMap).hasSize(probes.size());
        probes.forEach(pp -> assertSame(pp, ppMap.get(pp.getName())));
        verify(mockProbeLotValidator).validate(eq("lot1"), any());
        verify(mockProbePanelRepo).findAllByNameIn(Set.of("p1"));
    }

    @ParameterizedTest
    @CsvSource({"p1, lot2, 3, SGP,",
            ", lot2, 3, Faculty, Probe panel name missing.",
            "p!, lot2, 3, Faculty, Unknown probe panels: [\"p!\"]",
            "p1, , 3, Faculty, Probe lot number missing.",
            "p1, lot!, 3, SGP, Bad lot.",
            "p1, lot2,,SGP, Probe plex missing.",
            "p1, lot2, 0, SGP, Probe plex should be a positive number.",
            "p1, lot2, 2, , Probe cost is missing.",
    })
    public void testValidateProbes_problems(String probeName, String lot, Integer plex, SlideCosting cost, String expectedProblem) {
        ProbeOperationLabware pol = new ProbeOperationLabware("BC", "SGP1",
                List.of(new ProbeLot(probeName, lot, plex, cost)));
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
            pp = new ProbePanel(probeName);
            when(mockProbePanelRepo.findAllByNameIn(any())).thenReturn(List.of(pp));
        } else {
            pp = null;
            when(mockProbePanelRepo.findAllByNameIn(any())).thenReturn(List.of());
        }

        UCMap<ProbePanel> ppMap = service.validateProbes(problems, List.of(pol));
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
        List<ProbeOperationLabware> pols = List.of(new ProbeOperationLabware());
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        LocalDateTime time = LocalDateTime.now();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        UCMap<ProbePanel> ppMap = UCMap.from(ProbePanel::getName, new ProbePanel("banana"));
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        UCMap<Operation> lwOps = new UCMap<>();
        lwOps.put("STAN1", new Operation());
        doReturn(lwOps).when(service).makeOps(any(), any(), any(), any(), any());
        doNothing().when(service).linkWork(any(), any(), any());
        doNothing().when(service).saveProbes(any(), any(), any(), any());
        OperationResult opres = new OperationResult(List.of(), List.of());
        doReturn(opres).when(service).assembleResult(any(), any(), any());

        assertSame(opres, service.perform(user, pols, opType, time, lwMap, ppMap, workMap));
        verify(service).makeOps(user, opType, pols, lwMap, time);
        verify(service).linkWork(pols, lwOps, workMap);
        verify(service).saveProbes(pols, lwOps, lwMap, ppMap);
        verify(service).assembleResult(pols, lwMap, lwOps);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testMakeOps(boolean hasTime) {
        User user = EntityFactory.getUser();
        OperationType optype = EntityFactory.makeOperationType("opname", null);
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("STAN-1", null, null),
                new ProbeOperationLabware("STAN-2", null, null)
        );
        LabwareType lt = EntityFactory.getTubeType();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.makeEmptyLabware(lt, "STAN-1"),
                EntityFactory.makeEmptyLabware(lt, "STAN-2"));
        LocalDateTime time = (hasTime ? LocalDateTime.now() : null);
        Operation[] ops = { new Operation(), new Operation() };
        ops[0].setId(1);
        ops[1].setId(2);
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(ops[0], ops[1]);

        UCMap<Operation> opMap = service.makeOps(user, optype, pols, lwMap, time);
        verify(mockOpService).createOperationInPlace(optype, user, lwMap.get("STAN-1"), null, null);
        verify(mockOpService).createOperationInPlace(optype, user, lwMap.get("STAN-2"), null, null);

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
    public void testLinkWork() {
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("STAN-1", "SGP1", null),
                new ProbeOperationLabware("STAN-2", "SGP1", null),
                new ProbeOperationLabware("STAN-3", "SGP2", null)
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
    public void testSaveProbes() {
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("STAN-1", null, List.of(
                        new ProbeLot("probe1", "lot1", 1, SlideCosting.SGP),
                        new ProbeLot("probe2", "lot2", 2, SlideCosting.SGP)
                )),
                new ProbeOperationLabware("STAN-2", null, List.of(
                        new ProbeLot("probe1", "lot3", 3, SlideCosting.Faculty)
                ))
        );
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt, "STAN-1");
        Labware lw2 = EntityFactory.makeEmptyLabware(lt, "STAN-2");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);

        ProbePanel pp1 = new ProbePanel("probe1");
        ProbePanel pp2 = new ProbePanel("probe2");
        UCMap<ProbePanel> ppMap = UCMap.from(ProbePanel::getName, pp1, pp2);

        Operation op1 = new Operation();
        Operation op2 = new Operation();
        op1.setId(1);
        op2.setId(2);
        UCMap<Operation> lwOps = new UCMap<>(2);
        lwOps.put("STAN-1", op1);
        lwOps.put("STAN-2", op2);

        service.saveProbes(pols, lwOps, lwMap, ppMap);

        verify(mockLwProbeRepo).saveAll(List.of(
                new LabwareProbe(null, pp1, op1.getId(), lw1.getId(), "LOT1", 1, SlideCosting.SGP),
                new LabwareProbe(null, pp2, op1.getId(), lw1.getId(), "LOT2", 2, SlideCosting.SGP),
                new LabwareProbe(null, pp1, op2.getId(), lw2.getId(), "LOT3", 3, SlideCosting.Faculty)
        ));
    }

    @Test
    public void testAssembleResult() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = { EntityFactory.makeEmptyLabware(lt, "STAN-1"), EntityFactory.makeEmptyLabware(lt, "STAN-2")};
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, labware);
        List<ProbeOperationLabware> pols = List.of(
                new ProbeOperationLabware("STAN-1", null, null),
                new ProbeOperationLabware("STAN-2", null, null)
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