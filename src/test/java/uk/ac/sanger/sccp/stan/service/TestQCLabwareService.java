package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.OperationCommentRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest.QCLabware;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/**
 * Tests {@link QCLabwareServiceImp}
 */
public class TestQCLabwareService {
    @Mock
    private Clock mockClock;
    @Mock
    private ValidationHelperFactory mockValFactory;
    @Mock
    private OperationRepo mockOpRepo;
    @Mock
    private OperationCommentRepo mockOpComRepo;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private OperationService mockOpService;
    @Mock
    private ValidationHelper mockVal;
    private Set<String> problems;

    @InjectMocks
    private QCLabwareServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        problems = new LinkedHashSet<>();
        when(mockValFactory.getHelper()).thenReturn(mockVal);
        when(mockVal.getProblems()).thenReturn(problems);
        service = spy(service);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    public void testPerform_ok() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        Map<Integer, Comment> commentMap = Map.of(5, new Comment(5, "A", "B"));
        QCLabware qcl = new QCLabware(lw.getBarcode(), null, null, null);
        QCLabwareRequest request = new QCLabwareRequest(opType.getName(), List.of(qcl));
        List<QCLabware> qcls = request.getLabware();

        doReturn(opType).when(mockVal).checkOpType(anyString(), any(OperationTypeFlag.class));
        doReturn(lwMap).when(mockVal).checkLabware(anyCollection());
        doReturn(workMap).when(mockVal).checkWork(anyCollection());
        doNothing().when(service).checkTimestamps(any(), any(), any(), any());
        doReturn(commentMap).when(mockVal).checkCommentIds(any());

        OperationResult opres = new OperationResult(List.of(new Operation()), List.of(lw));
        doReturn(opres).when(service).record(any(), any(), any(), any(), any(), any());

        assertSame(opres, service.perform(user, request));
        verify(mockVal).checkOpType(request.getOperationType(), OperationTypeFlag.IN_PLACE);
        verify(mockVal).checkLabware(qcls.stream().map(QCLabware::getBarcode).collect(toList()));
        verify(mockVal).checkWork(qcls.stream().map(QCLabware::getWorkNumber).collect(toList()));
        verify(service).checkTimestamps(any(), same(qcls), same(lwMap), same(mockClock));
        verify(service).checkComments(any(), same(qcls));

        verify(service).record(user, opType, qcls, lwMap, workMap, commentMap);
    }


    @Test
    public void testPerform_none() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        QCLabwareRequest request = new QCLabwareRequest(opType.getName(), null);
        when(mockVal.checkOpType(any(), any(OperationTypeFlag.class))).thenReturn(opType);

        assertValidationException(() -> service.perform(user, request), List.of("No labware specified."));

        verify(mockVal).checkOpType(request.getOperationType(), OperationTypeFlag.IN_PLACE);
        verify(mockVal, never()).checkLabware(anyCollection());
        verify(mockVal, never()).checkWork(anyCollection());
        verify(service, never()).checkTimestamps(any(), any(), any(), any());
        verify(service, never()).checkComments(any(), any());
        verify(service, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testPerform_problems() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        Map<Integer, Comment> commentMap = Map.of(5, new Comment(5, "A", "B"));
        QCLabware qcl = new QCLabware(lw.getBarcode(), "SGP1", null, null);
        QCLabwareRequest request = new QCLabwareRequest(opType.getName(), List.of(qcl));
        List<QCLabware> qcls = request.getLabware();
        problems.add("Problem A.");
        problems.add("Problem B.");

        when(mockVal.checkOpType(any(), any(OperationTypeFlag.class))).thenReturn(opType);
        when(mockVal.checkLabware(anyCollection())).thenReturn(lwMap);
        when(mockVal.checkWork(anyCollection())).thenReturn(workMap);

        doAnswer(addProblem("Bad time")).when(service).checkTimestamps(any(), any(), any(), any());
        doAnswer(addProblem("Bad comment", commentMap)).when(service).checkComments(any(), any());

        assertValidationException(() -> service.perform(user, request),
                List.of("Problem A.", "Problem B.", "Bad time", "Bad comment"));

        verify(mockVal).checkOpType(request.getOperationType(), OperationTypeFlag.IN_PLACE);
        verify(mockVal).checkLabware(List.of(lw.getBarcode()));
        verify(mockVal).checkWork(List.of("SGP1"));
        verify(service).checkTimestamps(any(), same(qcls), same(lwMap), same(mockClock));
        verify(service).checkComments(any(), same(qcls));

        verify(service, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testCheckTimestamps() {
        ZoneId zone = ZoneId.systemDefault();
        Clock clock = Clock.fixed(ldt(2).toInstant(ZoneOffset.UTC), zone);
        final LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.rangeClosed(1,3)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lt, "STAN-"+i))
                .toArray(Labware[]::new);
        LocalDateTime[] times = IntStream.rangeClosed(1, lws.length)
                .mapToObj(TestQCLabwareService::ldt)
                .toArray(LocalDateTime[]::new);
        times[times.length-1] = null;

        List<QCLabware> qcls = IntStream.range(0, lws.length)
                .mapToObj(i -> new QCLabware(lws[i].getBarcode(), null, times[i], null))
                .collect(toList());

        doNothing().when(mockVal).checkTimestamp(any(), any(), any());
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);

        service.checkTimestamps(mockVal, qcls, lwMap, clock);

        LocalDate today = LocalDate.of(2023,1,2);
        for (int i = 0; i < lws.length; ++i) {
            verify(mockVal).checkTimestamp(times[i], today, lws[i]);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    public void testCheckComments(boolean anyRepeat, boolean anyProblem) {
        String expectedProblem = (anyProblem ? "Bad comment" : null);
        List<Integer> commentIds = anyRepeat ? List.of(1,2,3,2) : List.of(1,2,3);
        QCLabware qcl = new QCLabware("STAN-1", null, null, commentIds);
        var qcls = List.of(qcl);
        Map<Integer, Comment> commentMap = Map.of(
                1, new Comment(1, "A", "B"),
                2, new Comment(2, "C", "D")
        );
        when(mockVal.checkCommentIds(any())).thenReturn(commentMap);
        if (anyProblem) {
            problems.add(expectedProblem);
        }

        assertSame(commentMap, service.checkComments(mockVal, qcls));
        if (anyRepeat) {
            assertProblem(problems, "Duplicate comments specified for barcode STAN-1.");
        } else {
            assertProblem(problems, expectedProblem);
        }
        ArgumentCaptor<Stream<Integer>> idStreamCaptor = streamCaptor();
        verify(mockVal).checkCommentIds(idStreamCaptor.capture());
        assertThat(idStreamCaptor.getValue()).containsExactlyElementsOf(commentIds);
    }

    private static LocalDateTime ldt(int day) {
        return LocalDateTime.of(2023,1,day,12,0);
    }

    @Test
    public void testRecord() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("foo", null);
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.rangeClosed(1,3)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lt, "STAN-"+i))
                .toArray(Labware[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        Work work = EntityFactory.makeWork("SGP1");
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work);
        Map<Integer, Comment> commentMap = Map.of(5, new Comment(5, "A", "B"));

        List<QCLabware> qcls = List.of(new QCLabware("STAN-1", "SGP1", ldt(1), List.of(1,2)),
                new QCLabware("STAN-2", "SGP1", ldt(2), null),
                new QCLabware("STAN-3", null, null, List.of(3)));
        Operation[] ops = IntStream.rangeClosed(1, lws.length)
                .mapToObj(i -> new Operation(i, opType, null, null, null))
                .toArray(Operation[]::new);
        for (int i = 0; i < lws.length; ++i) {
            when(mockOpService.createOperationInPlace(opType, user, lws[i], null, null)).thenReturn(ops[i]);
        }
        doNothing().when(service).linkWorks(any(), any(), any());
        doNothing().when(service).linkComments(any(), any(), any(), any());
        OperationResult opRes = new OperationResult(Arrays.asList(ops), Arrays.asList(lws));
        doReturn(opRes).when(service).assembleResult(any(), any(), any());

        assertSame(opRes, service.record(user, opType, qcls, lwMap, workMap, commentMap));
        UCMap<Operation> lwOps = new UCMap<>(lws.length);
        for (int i = 0; i < lws.length; ++i) {
            lwOps.put(lws[i].getBarcode(), ops[i]);
        }

        for (Labware lw : lws) {
            verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        }
        for (int i = 0; i < 2; ++i) {
            assertEquals(qcls.get(i).getCompletion(), ops[i].getPerformed());
        }
        verify(mockOpRepo).saveAll(List.of(ops[0], ops[1]));
        verify(service).linkWorks(qcls, workMap, lwOps);
        verify(service).linkComments(qcls, commentMap, lwOps, lwMap);
        verify(service).assembleResult(qcls, lwOps, lwMap);
    }

    @Test
    public void testLinkWorks() {
        Work[] works = IntStream.rangeClosed(1,2)
                .mapToObj(i -> new Work(i, "SGP"+i, null, null, null, null, null, null))
                .toArray(Work[]::new);
        Operation[] ops = IntStream.rangeClosed(1,3)
                .mapToObj(i -> new Operation(i, null, null, null, null))
                .toArray(Operation[]::new);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, works);
        UCMap<Operation> lwOps = new UCMap<>(3);
        for (int i = 0; i < ops.length; ++i) {
            lwOps.put("STAN-"+(i+1), ops[i]);
        }
        List<QCLabware> qcls = List.of(
                new QCLabware("STAN-1", "SGP1", null, null),
                new QCLabware("STAN-2", "SGP2", null, null),
                new QCLabware("STAN-3", "SGP1", null, null)
        );
        service.linkWorks(qcls, workMap, lwOps);

        verify(mockWorkService).link(works[0], List.of(ops[0], ops[2]));
        verify(mockWorkService).link(works[1], List.of(ops[1]));
        verifyNoMoreInteractions(mockWorkService);
    }

    @Test
    public void testLinkComments() {
        Comment[] comments = IntStream.rangeClosed(1,3)
                .mapToObj(i -> new Comment(i, "com"+i, "cat"))
                .toArray(Comment[]::new);
        Map<Integer, Comment> commentMap = Arrays.stream(comments).collect(inMap(Comment::getId));
        Operation[] ops = IntStream.rangeClosed(11,13)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(i);
                    return op;
                })
                .toArray(Operation[]::new);
        Labware[] lws = IntStream.rangeClosed(21,23)
                .mapToObj(i -> {
                    Labware lw = new Labware();
                    lw.setBarcode("STAN-"+i);
                    lw.setId(i);
                    return lw;
                })
                .toArray(Labware[]::new);
        UCMap<Operation> lwOps = new UCMap<>(lws.length);
        IntStream.range(0, lws.length)
                .forEach(i -> lwOps.put(lws[i].getBarcode(), ops[i]));
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);

        List<QCLabware> qcls = List.of(
                new QCLabware("STAN-21", null, null, List.of(1,2,3)),
                new QCLabware("STAN-22", null, null, null),
                new QCLabware("STAN-23", null, null, List.of(2))
        );

        service.linkComments(qcls, commentMap, lwOps, lwMap);
        //noinspection unchecked
        ArgumentCaptor<Collection<OperationComment>> captor = ArgumentCaptor.forClass(Collection.class);

        verify(mockOpComRepo).saveAll(captor.capture());
        Collection<OperationComment> opComs = captor.getValue();
        assertThat(opComs).containsExactlyInAnyOrder(
                new OperationComment(null, comments[0], ops[0].getId(), null, null, lws[0].getId()),
                new OperationComment(null, comments[1], ops[0].getId(), null, null, lws[0].getId()),
                new OperationComment(null, comments[2], ops[0].getId(), null, null, lws[0].getId()),

                new OperationComment(null, comments[1], ops[2].getId(), null, null, lws[2].getId())
        );
    }

    @Test
    public void testAssembleResult() {
        Labware[] lws = IntStream.rangeClosed(1,3)
                .mapToObj(i -> new Labware(i, "STAN-"+i, null, null))
                .toArray(Labware[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        UCMap<Operation> lwOps = new UCMap<>(lws.length);
        Operation[] ops = IntStream.range(0, lws.length)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(100+i);
                    lwOps.put(lws[i].getBarcode(), op);
                    return op;
                })
                .toArray(Operation[]::new);
        List<QCLabware> qcls = Arrays.stream(lws)
                .map(lw -> new QCLabware(lw.getBarcode(), null, null, null))
                .collect(toList());

        OperationResult opres = service.assembleResult(qcls, lwOps, lwMap);
        assertThat(opres.getOperations()).containsExactly(ops);
        assertThat(opres.getLabware()).containsExactly(lws);
    }
}