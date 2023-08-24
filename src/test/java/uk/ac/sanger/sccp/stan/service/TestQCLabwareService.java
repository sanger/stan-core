package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest.QCLabware;
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
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private OperationRepo mockOpRepo;
    @Mock
    private OperationCommentRepo mockOpComRepo;
    @Mock
    private LabwareValidatorFactory mockLwValFactory;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private OperationService mockOpService;
    @Mock
    private CommentValidationService mockCommentValidationService;
    @InjectMocks
    private QCLabwareServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
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

        doReturn(opType).when(service).checkOpType(any(), any());
        doReturn(lwMap).when(service).checkLabware(any(), any());
        doReturn(workMap).when(service).checkWork(any(), any());
        doNothing().when(service).checkTimestamps(any(), any(), any(), any());
        doReturn(commentMap).when(service).checkComments(any(), any());

        OperationResult opres = new OperationResult(List.of(new Operation()), List.of(lw));
        doReturn(opres).when(service).record(any(), any(), any(), any(), any(), any());

        assertSame(opres, service.perform(user, request));
        verify(service).checkOpType(any(), eq(request.getOperationType()));
        verify(service).checkLabware(any(), same(qcls));
        verify(service).checkWork(any(), same(qcls));
        verify(service).checkTimestamps(any(), same(qcls), same(lwMap), same(mockClock));
        verify(service).checkComments(any(), same(qcls));

        verify(service).record(user, opType, qcls, lwMap, workMap, commentMap);
    }


    @Test
    public void testPerform_none() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        QCLabwareRequest request = new QCLabwareRequest(opType.getName(), null);

        doReturn(opType).when(service).checkOpType(any(), any());

        assertValidationException(() -> service.perform(user, request), List.of("No labware specified."));

        verify(service).checkOpType(any(), eq(request.getOperationType()));
        verify(service, never()).checkLabware(any(), any());
        verify(service, never()).checkWork(any(), any());
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
        QCLabware qcl = new QCLabware(lw.getBarcode(), null, null, null);
        QCLabwareRequest request = new QCLabwareRequest(opType.getName(), List.of(qcl));
        List<QCLabware> qcls = request.getLabware();

        doAnswer(addProblem("Bad op type", opType)).when(service).checkOpType(any(), any());
        doAnswer(addProblem("Bad lw", lwMap)).when(service).checkLabware(any(), any());
        doAnswer(addProblem("Bad work", workMap)).when(service).checkWork(any(), any());
        doAnswer(addProblem("Bad time")).when(service).checkTimestamps(any(), any(), any(), any());
        doAnswer(addProblem("Bad comment", commentMap)).when(service).checkComments(any(), any());

        assertValidationException(() -> service.perform(user, request),
                List.of("Bad op type", "Bad lw", "Bad work", "Bad time", "Bad comment"));

        verify(service).checkOpType(any(), eq(request.getOperationType()));
        verify(service).checkLabware(any(), same(qcls));
        verify(service).checkWork(any(), same(qcls));
        verify(service).checkTimestamps(any(), same(qcls), same(lwMap), same(mockClock));
        verify(service).checkComments(any(), same(qcls));

        verify(service, never()).record(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "boop, true, ",
            ",,No operation type specified.",
            "boop,,Unknown operation type: \"boop\"",
            "boop, false, Operation type boop cannot be used in this request.",
    })
    public void testCheckOpType(String opName, Boolean inPlace, String expectedProblem) {
        OperationType opType;
        if (inPlace==null) {
            opType = null;
        } else if (inPlace) {
            opType = EntityFactory.makeOperationType(opName, null, OperationTypeFlag.IN_PLACE);
        } else {
            opType = EntityFactory.makeOperationType(opName, null);
        }
        when(mockOpTypeRepo.findByName(opName)).thenReturn(Optional.ofNullable(opType));
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(opType, service.checkOpType(problems, opName));
        assertProblem(problems, expectedProblem);
    }

    @ParameterizedTest
    @CsvSource({
            "STAN-1,,",
            "STAN-1,Bad barcode,Bad barcode",
            "STAN-1 null STAN-2,,Missing labware barcode.",
            "null,,Missing labware barcode.",
    })
    public void testCheckLabware(String barcodesJoined, String validationError, String expectedProblem) {
        String[] barcodesSplit = barcodesJoined.split(" ");
        List<QCLabware> qcls = Arrays.stream(barcodesSplit)
                .map(s -> "null".equals(s) ? null : s)
                .map(bc -> new QCLabware(bc, null, null, null))
                .collect(toList());
        List<String> nonNullBarcodes = qcls.stream()
                .map(QCLabware::getBarcode)
                .filter(Objects::nonNull)
                .collect(toList());
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        when(val.getErrors()).thenReturn(validationError==null ? List.of() : List.of(validationError));
        Labware lw = EntityFactory.getTube();
        when(val.getLabware()).thenReturn(List.of(lw));
        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        UCMap<Labware> lwMap = service.checkLabware(problems, qcls);
        assertProblem(problems, expectedProblem);
        if (nonNullBarcodes.isEmpty()) {
            assertThat(lwMap).isEmpty();
            verifyNoInteractions(val);
        } else {
            assertThat(lwMap).containsExactly(Map.entry(lw.getBarcode(), lw));
            verify(val).loadLabware(mockLwRepo, nonNullBarcodes);
            verify(val).validateSources();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false,true})
    public void testCheckWork(boolean anyProblem) {
        List<String> workNumbers = List.of("SGP1", "SGP2");
        List<QCLabware> qcls = workNumbers.stream()
                .map(wn -> new QCLabware(null, wn, null, null))
                .collect(toList());
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        String expectedProblem = anyProblem ? "Bad work" : null;
        mayAddProblem(expectedProblem, workMap).when(mockWorkService).validateUsableWorks(any(), any());
        List<String> problems = new ArrayList<>(anyProblem ? 1 : 0);
        assertSame(workMap, service.checkWork(problems, qcls));
        assertProblem(problems, expectedProblem);
        verify(mockWorkService).validateUsableWorks(any(), eq(workNumbers));
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
        final List<String> problems = new ArrayList<>(0);

        doNothing().when(service).checkTimestamp(any(), any(), any(), any());
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);

        service.checkTimestamps(problems, qcls, lwMap, clock);
        LocalDate today = LocalDate.of(2023,1,2);
        for (int i = 0; i < lws.length; ++i) {
            verify(service).checkTimestamp(same(problems), same(qcls.get(i).getCompletion()), eq(today), eq(lws[i]));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1,2,3,",
            "1,2,2,",
            ",2,3,",
            "1,,1,",
            "2,1,3,Specified time is before labware STAN-1 was created.",
            "1,3,2,Specified time is in the future.",
    })
    public void testCheckTimestamp(Integer lwDay, Integer givenDay, int nowDay, String expectedProblem) {
        Labware lw;
        if (lwDay==null) {
            lw = null;
        } else {
            lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType(), "STAN-1");
            lw.setCreated(ldt(lwDay));
        }

        LocalDate today = LocalDate.of(2023,1,nowDay);
        LocalDateTime time = (givenDay==null ? null : ldt(givenDay));

        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);

        service.checkTimestamp(problems, time, today, lw);

        assertProblem(problems, expectedProblem);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    public void testCheckComments(boolean anyRepeat, boolean anyProblem) {
        String expectedProblem = (anyProblem ? "Bad comment" : null);
        List<Integer> commentIds = anyRepeat ? List.of(1,2,3,2) : List.of(1,2,3);
        QCLabware qcl = new QCLabware("STAN-1", null, null, commentIds);
        var qcls = List.of(qcl);
        List<Comment> comments = List.of(new Comment(1, "A", "B"), new Comment(2, "C", "D"));
        mayAddProblem(expectedProblem, comments).when(mockCommentValidationService).validateCommentIds(any(), any());

        List<String> problems = new ArrayList<>(anyRepeat || anyProblem ? 1 : 0);
        var commentMap = service.checkComments(problems, qcls);
        assertThat(commentMap).containsExactly(Map.entry(1, comments.get(0)), Map.entry(2, comments.get(1)));
        if (anyRepeat) {
            assertProblem(problems, "Duplicate comments specified for barcode STAN-1.");
        } else {
            assertProblem(problems, expectedProblem);
        }
        ArgumentCaptor<Stream<Integer>> idStreamCaptor = streamCaptor();
        verify(mockCommentValidationService).validateCommentIds(same(problems), idStreamCaptor.capture());
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