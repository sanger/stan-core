package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.CompletionRequest;
import uk.ac.sanger.sccp.stan.request.CompletionRequest.LabwareSampleComments;
import uk.ac.sanger.sccp.stan.request.CompletionRequest.SampleAddressComment;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.stan.service.CompletionServiceImp.PROBE_HYBRIDISATION_NAMES;
import static uk.ac.sanger.sccp.utils.BasicUtils.concat;

/**
 * Tests {@link CompletionServiceImp}
 */
public class TestCompletionService {
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private OperationRepo mockOpRepo;
    @Mock
    private Clock mockClock;
    @Mock
    private OperationCommentRepo mockOpComRepo;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private CommentValidationService mockCommentValidationService;
    @Mock
    private OperationService mockOpService;

    @InjectMocks
    private CompletionServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    public void setUp() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
    }

    @AfterEach
    public void cleanUp() throws Exception {
        mocking.close();
    }

    @Test
    public void testPerform() {
        User user = EntityFactory.getUser();
        CompletionRequest request = new CompletionRequest("SGP1", "comp",
                List.of(new LabwareSampleComments("STAN-1", null, null)));
        Work work = EntityFactory.makeWork("SGP1");
        OperationType opType = EntityFactory.makeOperationType("comp", null, OperationTypeFlag.IN_PLACE);
        List<OperationType> precedingOpTypes = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeOperationType("prev"+i, null, OperationTypeFlag.IN_PLACE))
                .toList();
        Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        Map<Integer, Operation> priorOps = Map.of(200, new Operation());
        Map<Integer, Comment> commentMap = Map.of(300, new Comment());
        doReturn(work).when(mockWorkService).validateUsableWork(any(), any());
        doReturn(opType).when(service).loadOpType(any(),any());
        doReturn(lwMap).when(service).loadLabware(any(), ArgumentMatchers.<List<String>>any());
        doReturn(precedingOpTypes).when(service).getPrecedingOpTypes(any(), any());
        doReturn(priorOps).when(service).lookUpLatestOps(any(), any(), any(), anyBoolean());
        doNothing().when(service).validateTimestamps(any(), any(), any(), any());
        doNothing().when(service).validateCommentLocations(any(), any(), any());
        doReturn(commentMap).when(service).validateCommentIds(any(), any());

        OperationResult opres = new OperationResult(List.of(), List.of(lw));
        doReturn(opres).when(service).execute(any(), any(), any(), any(), any(), any());

        assertSame(opres, service.perform(user, request));

        verify(mockWorkService).validateUsableWork(any(), eq("SGP1"));
        verify(service).validateOpType(any(), eq("comp"));
        verify(service).loadLabware(any(), same(request.getLabware()));
        verify(service).getPrecedingOpTypes(any(), same(opType));
        verify(service).lookUpLatestOps(any(), same(precedingOpTypes), eq(lwMap.values()), eq(true));
        verify(service).validateTimestamps(any(), same(request.getLabware()), same(lwMap), same(priorOps));
        verify(service).validateCommentLocations(any(), same(request.getLabware()), same(lwMap));
        verify(service).validateCommentIds(any(), same(request.getLabware()));

        verify(service).execute(user, request.getLabware(), opType, work, lwMap, commentMap);
    }

    @Test
    public void testPerform_null() {
        Matchers.assertValidationException(() -> service.perform(EntityFactory.getUser(), null),
                List.of("Request not specified."));
        verify(service, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testPerform_problems(boolean precedingOpTypeExists) {
        when(mockWorkService.validateUsableWork(any(), any())).thenAnswer(addProblem("Bad work"));
        OperationType opType = EntityFactory.makeOperationType("Foozle", null);
        List<OperationType> precedingOpTypes = precedingOpTypeExists ? List.of(EntityFactory.makeOperationType("preceding", null, OperationTypeFlag.IN_PLACE)) : null;
        doAnswer(addProblem("Bad op type", opType)).when(service).validateOpType(any(), any());
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        doAnswer(addProblem("Bad lw", lwMap)).when(service).loadLabware(any(), ArgumentMatchers.<List<LabwareSampleComments>>any());
        doAnswer(addProblem("Bad preceding op type", precedingOpTypes)).when(service).getPrecedingOpTypes(any(), any());

        Map<Integer, Operation> precedingOps = precedingOpTypeExists ? Map.of(3, new Operation()) : Map.of();
        doAnswer(addProblem("Bad latest ops", precedingOps)).when(service).lookUpLatestOps(any(), any(), any(), anyBoolean());
        doAnswer(addProblem("Bad time")).when(service).validateTimestamps(any(), any(), any(), any());
        doAnswer(addProblem("Bad comment locations")).when(service).validateCommentLocations(any(), any(), any());
        doAnswer(addProblem("Bad comment ids", Map.of())).when(service).validateCommentIds(any(), any());

        CompletionRequest request = new CompletionRequest("SGP1", "foozle", List.of(new LabwareSampleComments("STAN-1", null, null)));

        List<String> expectedProblems = List.of("User not specified.",
                "Bad work",
                "Bad op type",
                "Bad lw",
                "Bad preceding op type",
                "Bad time",
                "Bad comment locations",
                "Bad comment ids");
        if (precedingOpTypeExists) {
            expectedProblems = concat(expectedProblems, List.of("Bad latest ops"));
        }

        Matchers.assertValidationException(() -> service.perform(null, request),
                expectedProblems);

        verify(mockWorkService).validateUsableWork(any(), eq("SGP1"));
        verify(service).validateOpType(any(), eq("foozle"));
        verify(service).loadLabware(any(), same(request.getLabware()));
        verify(service).getPrecedingOpTypes(any(), same(opType));
        verify(service, times(precedingOpTypeExists ? 1 : 0)).lookUpLatestOps(any(), eq(precedingOpTypes), eq(lwMap.values()), eq(true));
        verify(service).validateTimestamps(any(), same(request.getLabware()), same(lwMap), same(precedingOps));
        verify(service).validateCommentLocations(any(), same(request.getLabware()), same(lwMap));
        verify(service).validateCommentIds(any(), same(request.getLabware()));
        verify(service, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"true,,",
            "false,,Operation type fizzle cannot be used in this operation.",
            ",Bad op name,Bad op name"})
    public void testValidateOpType(Boolean inPlace, String loadError, String expectedError) {
        OperationType opType;
        final String opname = "fizzle";
        if (inPlace==null) {
            opType = null;
        } else if (inPlace) {
            opType = EntityFactory.makeOperationType(opname, null, OperationTypeFlag.IN_PLACE);
        } else {
            opType = EntityFactory.makeOperationType(opname, null);
        }
        mayAddProblem(loadError, opType).when(service).loadOpType(any(), eq(opname));
        List<String> problems = new ArrayList<>(expectedError==null ? 0 : 1);
        assertSame(opType, service.validateOpType(problems, opname));
        Matchers.assertProblem(problems, expectedError);
        verify(service).loadOpType(any(), eq(opname));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "Probe hybridisation QC; true;",
            "Probe hybridisation QC; false; Operation type missing from database: [Probe hybridisation Xenium, Probe hybridisation Cytassist]",
            ";false;",
            "foozle; false; Operation type foozle cannot be used in this operation.",
    }, delimiter=';')
    public void testGetPrecedingOpTypes(String opName, boolean priorExists, String expectedProblem) {
        OperationType opType = (opName == null ? null : EntityFactory.makeOperationType(opName, null));
        List<OperationType> priorOpTypes;
        if (priorExists) {
            priorOpTypes = PROBE_HYBRIDISATION_NAMES.stream()
                    .map(name -> EntityFactory.makeOperationType(name, null))
                    .toList();
            when(mockOpTypeRepo.findByNameIn(PROBE_HYBRIDISATION_NAMES)).thenReturn(priorOpTypes);
        } else {
            priorOpTypes = null;
            when(mockOpTypeRepo.findByNameIn(any())).thenReturn(List.of());
        }
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);

        assertSame(priorOpTypes, service.getPrecedingOpTypes(problems, opType));
        Matchers.assertProblem(problems, expectedProblem);
    }

    @Test
    public void testLoadLabware() {
        List<String> problems = List.of("existing problems");
        List<String> barcodes = List.of("STAN-1", "STAN-2");
        List<LabwareSampleComments> lscs = barcodes.stream()
                .map(bc -> new LabwareSampleComments(bc, null, null))
                .collect(toList());
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        doReturn(lwMap).when(service).loadLabware(problems, barcodes);
        assertSame(lwMap, service.loadLabware(problems, lscs));
    }

    @ParameterizedTest
    @MethodSource("validateTimestampsArgs")
    public void testValidateTimestamps(Collection<LabwareSampleComments> lscs, LocalDateTime now,
                                       UCMap<Labware> lwMap, Map<Integer, Operation> priorOpMap,
                                       List<String> expectedErrors) {
        setMockClock(mockClock, now);
        final List<String> problems = new ArrayList<>(expectedErrors.size());
        service.validateTimestamps(problems, lscs, lwMap, priorOpMap);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedErrors);
    }

    static Stream<Arguments> validateTimestampsArgs() {
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        lw2.setBarcode("STAN-A1");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        Operation priorOp = new Operation();
        priorOp.setPerformed(time(2));
        Map<Integer, Operation> priorOpMap = Map.of(lw2.getId(), priorOp);
        return Arrays.stream(new Object[][] {
                { List.of(lsc("STAN-A1", time(3)), lsc("STAN-Z", null)),
                        time(4), List.of() },
                { List.of(lsc("STAN-A1", time(1))), time(4),
                        List.of("The time given for labware STAN-A1 is before its prior operation.") },
                { List.of(lsc(lw1.getBarcode(), time(5))), time(4),
                        List.of("Specified date is in the future: ["+time(5)+"]")},
                { List.of(lsc(lw1.getBarcode(), time(5)), lsc(lw2.getBarcode(), time(6))), time(3),
                        List.of("Specified dates are in the future: "+List.of(time(5), time(6))) },
        }).map(arr -> Arguments.of(arr[0], arr[1], lwMap, priorOpMap, arr[2]));
    }

    private static LabwareSampleComments lsc(String barcode, LocalDateTime time) {
        return new LabwareSampleComments(barcode, time, null);
    }

    private static LocalDateTime time(int index) {
        return LocalDateTime.of(2023,1,index,12,0);
    }

    @ParameterizedTest
    @MethodSource("validateCommentLocationsArgs")
    public void testValidateCommentLocations(List<LabwareSampleComments> lscs,
                                             List<String> expectedProblems, UCMap<Labware> lwMap) {
        final List<String> problems = new ArrayList<>(expectedProblems.size());
        service.validateCommentLocations(problems, lscs, lwMap);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateCommentLocationsArgs() {
        Sample sam = EntityFactory.getSample();
        final Integer samId = sam.getId();
        Labware lw1 = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,2), sam);
        lw1.setBarcode("STAN-1");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1);
        final Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3);
        return Arrays.stream(new Object[][] {
                { List.of(new LabwareSampleComments("STAN-1", null,
                        List.of(new SampleAddressComment(samId, A1, 1))), new LabwareSampleComments()), List.of() },
                { List.of(new LabwareSampleComments("STAN-1", null,
                        List.of(new SampleAddressComment(samId, A2, 1), new SampleAddressComment(samId, A3, 2)))),
                        List.of("No slot A3 in labware STAN-1.", "Sample id "+samId+" is not present in STAN-1 slot A2.")},
                { List.of(new LabwareSampleComments("STAN-X", null, List.of(new SampleAddressComment(samId, A1, 1)))),
                        List.of()},
                { List.of(new LabwareSampleComments("STAN-1", null, List.of(new SampleAddressComment(null, A1, 5)))),
                        List.of("Sample id not specified.")},
                { List.of(new LabwareSampleComments("STAN-1", null, List.of(new SampleAddressComment(3, null, 6)))),
                        List.of("Slot address for comment not specified.")},
                { List.of(new LabwareSampleComments("STAN-1", null,
                        List.of(new SampleAddressComment(samId, A1, 4), new SampleAddressComment(samId, A1, 5),
                                new SampleAddressComment(samId, A1, 4)))),
                        List.of("Repeated comment specified in STAN-1: ["+new SampleAddressComment(samId, A1, 4)+"]")}
        }).map(arr -> Arguments.of(arr[0], arr[1], lwMap));
    }

    @Test
    public void testValidateCommentIds() {
        List<LabwareSampleComments> lscs = List.of(
                new LabwareSampleComments(null, null, List.of(
                        new SampleAddressComment(null, null, 1),
                        new SampleAddressComment(null, null, 2)
                )),
                new LabwareSampleComments(null, null, List.of(
                        new SampleAddressComment(null, null, 3)
                ))
        );
        final List<String> problems = new ArrayList<>(1);
        final Comment comment = new Comment(3, "hello", "cat");
        when(mockCommentValidationService.validateCommentIds(any(), any()))
                .then(Matchers.addProblem("Bad comment ids", List.of(comment)));
        assertThat(service.validateCommentIds(problems, lscs)).hasSize(1).containsEntry(3, comment);
        ArgumentCaptor<Stream<Integer>> streamCaptor = Matchers.streamCaptor();
        verify(mockCommentValidationService).validateCommentIds(any(), streamCaptor.capture());
        assertThat(streamCaptor.getValue()).containsExactly(1,2,3);
    }

    @Test
    public void testExecute() {
        User user = EntityFactory.getUser();
        List<LabwareSampleComments> lscs = List.of(new LabwareSampleComments());
        OperationType opType = EntityFactory.makeOperationType("fizzle", null);
        Work work = EntityFactory.makeWork("SGP1");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        Map<Integer, Comment> commentMap = Map.of(4, new Comment(4, "A", "B"));
        UCMap<Operation> bcOps = new UCMap<>(1);
        bcOps.put("STAN-1", new Operation());
        doReturn(bcOps).when(service).makeOps(any(), any(), any(), any());
        doNothing().when(service).recordComments(any(), any(), any(), any());
        OperationResult opres = new OperationResult(bcOps.values(), lwMap.values());
        doReturn(opres).when(service).composeResult(any(), any(), any());

        assertSame(opres, service.execute(user, lscs, opType, work, lwMap, commentMap));

        verify(service).makeOps(user, opType, lscs, lwMap);
        verify(service).recordComments(lscs, lwMap, bcOps, commentMap);
        verify(mockWorkService).link(work, bcOps.values());
        verify(service).composeResult(lscs, lwMap, bcOps);
    }

    @Test
    public void testMakeOps() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("fizzle", null);
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), EntityFactory.getSample());
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        LocalDateTime time1 = time(1);
        LocalDateTime now = time(2);
        setMockClock(mockClock, now);
        List<LabwareSampleComments> lscs = List.of(
                new LabwareSampleComments(lw1.getBarcode(), time1, null),
                new LabwareSampleComments(lw2.getBarcode(), null, null)
        );
        Operation op1 = new Operation();
        Operation op2 = new Operation();
        op1.setId(1);
        op2.setId(2);
        op1.setPerformed(now);
        op2.setPerformed(now);

        when(mockOpService.createOperationInPlace(opType, user, lw1, null, null))
                .thenReturn(op1);
        when(mockOpService.createOperationInPlace(opType, user, lw2, null, null))
                .thenReturn(op2);

        UCMap<Operation> bcOps = service.makeOps(user, opType, lscs, lwMap);
        assertThat(bcOps).hasSize(2).containsEntry(lw1.getBarcode(), op1).containsEntry(lw2.getBarcode(), op2);
        assertEquals(time1, op1.getPerformed());
        assertEquals(now, op2.getPerformed());
        verify(mockOpRepo).saveAll(List.of(op1));
    }

    @Test
    public void testRecordComments() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt, "STAN-1");
        Labware lw2 = EntityFactory.makeEmptyLabware(lt, "STAN-2");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);

        UCMap<Operation> bcOps = new UCMap<>(2);
        Operation[] ops = IntStream.range(1, 3).mapToObj(i -> {
                Operation op = new Operation();
                op.setId(i);
                return op;
        }).toArray(Operation[]::new);
        bcOps.put("STAN-1", ops[0]);
        bcOps.put("STAN-2", ops[1]);

        Comment com1 = new Comment(1, "A", "B");
        Comment com2 = new Comment(2, "C", "D");

        Map<Integer, Comment> commentMap = Map.of(1, com1, 2, com2);
        final Address A1 = new Address(1,1);
        Integer slot1id = lw1.getFirstSlot().getId();
        Integer slot2id = lw2.getFirstSlot().getId();

        List<LabwareSampleComments> lscs = List.of(
                new LabwareSampleComments("STAN-1", null, List.of(new SampleAddressComment(1, A1, 1),
                        new SampleAddressComment(3, A1, 2))),
                new LabwareSampleComments("STAN-2", null, List.of(new SampleAddressComment(5, A1, 1)))
        );

        service.recordComments(lscs, lwMap, bcOps, commentMap);
        List<OperationComment> expectedOpComs = List.of(
                new OperationComment(null, com1, 1, 1, slot1id, null),
                new OperationComment(null, com2, 1, 3, slot1id, null),
                new OperationComment(null, com1, 2, 5, slot2id, null)
        );
        verify(mockOpComRepo).saveAll(expectedOpComs);
    }

    @Test
    public void testComposeResult() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt, "STAN-1");
        Labware lw2 = EntityFactory.makeEmptyLabware(lt, "STAN-2");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);

        UCMap<Operation> bcOps = new UCMap<>(2);
        Operation[] ops = IntStream.range(1, 3).mapToObj(i -> {
            Operation op = new Operation();
            op.setId(i);
            return op;
        }).toArray(Operation[]::new);
        bcOps.put("STAN-1", ops[0]);
        bcOps.put("STAN-2", ops[1]);

        List<LabwareSampleComments> lscs = List.of(
                new LabwareSampleComments("STAN-2", null, null),
                new LabwareSampleComments("STAN-1", null, null)
        );

        OperationResult opres = service.composeResult(lscs, lwMap, bcOps);

        assertThat(opres.getOperations()).containsExactly(ops[1], ops[0]);
        assertThat(opres.getLabware()).containsExactly(lw2, lw1);
    }
}