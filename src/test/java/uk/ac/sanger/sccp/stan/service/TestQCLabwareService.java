package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.OperationCommentRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest.QCLabware;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest.QCSampleComment;
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
import static uk.ac.sanger.sccp.stan.service.operation.AnalyserServiceImp.RUN_NAME;
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
    private LabwareNoteService mockLwNoteService;
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
        QCLabware qcl = new QCLabware(lw.getBarcode(), null, null, null, null, null);
        QCLabwareRequest request = new QCLabwareRequest(opType.getName(), List.of(qcl));
        List<QCLabware> qcls = request.getLabware();

        doReturn(opType).when(mockVal).checkOpType(anyString(), any(OperationTypeFlag.class));
        doReturn(lwMap).when(mockVal).checkLabware(anyCollection());
        doReturn(workMap).when(mockVal).checkWork(anyCollection());
        doNothing().when(service).checkTimestamps(any(), any(), any(), any());
        doReturn(commentMap).when(mockVal).checkCommentIds(any());
        doNothing().when(service).checkSampleComments(any(), any(), any());
        doNothing().when(service).checkRunNames(any(), any(), any());

        OperationResult opres = new OperationResult(List.of(new Operation()), List.of(lw));
        doReturn(opres).when(service).record(any(), any(), any(), any(), any(), any());

        assertSame(opres, service.perform(user, request));
        verify(mockVal).checkOpType(request.getOperationType(), OperationTypeFlag.IN_PLACE);
        verify(mockVal).checkLabware(qcls.stream().map(QCLabware::getBarcode).collect(toList()));
        verify(mockVal).checkWork(qcls.stream().map(QCLabware::getWorkNumber).collect(toList()));
        verify(service).checkTimestamps(mockVal, qcls, lwMap, mockClock);
        verify(service).checkComments(mockVal, qcls);
        verify(service).checkSampleComments(mockVal, qcls, lwMap);
        verify(service).checkRunNames(problems, qcls, lwMap);

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
        verify(service, never()).checkSampleComments(any(), any(), any());
        verify(service, never()).checkRunNames(any(), any(), any());
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
        QCLabware qcl = new QCLabware(lw.getBarcode(), null, "SGP1", null, null, null);
        QCLabwareRequest request = new QCLabwareRequest(opType.getName(), List.of(qcl));
        List<QCLabware> qcls = request.getLabware();
        problems.add("Problem A.");
        problems.add("Problem B.");

        when(mockVal.checkOpType(any(), any(OperationTypeFlag.class))).thenReturn(opType);
        when(mockVal.checkLabware(anyCollection())).thenReturn(lwMap);
        when(mockVal.checkWork(anyCollection())).thenReturn(workMap);

        doAnswer(addProblem("Bad time")).when(service).checkTimestamps(any(), any(), any(), any());
        doAnswer(addProblem("Bad comment", commentMap)).when(service).checkComments(any(), any());
        doAnswer(addProblem("Bad run name")).when(service).checkRunNames(any(), any(), any());

        assertValidationException(() -> service.perform(user, request),
                List.of("Problem A.", "Problem B.", "Bad time", "Bad comment", "Bad run name"));

        verify(mockVal).checkOpType(request.getOperationType(), OperationTypeFlag.IN_PLACE);
        verify(mockVal).checkLabware(List.of(lw.getBarcode()));
        verify(mockVal).checkWork(List.of("SGP1"));
        verify(service).checkTimestamps(any(), same(qcls), same(lwMap), same(mockClock));
        verify(service).checkComments(any(), same(qcls));
        verify(service).checkRunNames(any(), same(qcls), same(lwMap));

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
                .mapToObj(i -> new QCLabware(lws[i].getBarcode(), null, null, times[i], null, null))
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
    @CsvSource({"false,false,false", "true,false,false", "false,true,false", "false,false,true"})
    public void testCheckComments(boolean anyRepeat,  boolean scRepeat, boolean anyProblem) {
        String expectedProblem = (anyProblem ? "Bad comment" :
                (anyRepeat || scRepeat) ? "Duplicate comments specified for barcode STAN-1."
                        : null);
        List<Integer> commentIds = anyRepeat ? List.of(1,2,3,2) : List.of(1,2,3);
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        List<QCSampleComment> scs = List.of(
                new QCSampleComment(A1, 4, 5),
                new QCSampleComment(A2, 4, 5),
                new QCSampleComment(A1,5,5),
                new QCSampleComment(A1,4, scRepeat ? 5 : 6)
        );
        List<Integer> combinedCommentIds = Stream.concat(commentIds.stream(),
                scs.stream().map(QCSampleComment::getCommentId)).toList();
        QCLabware qcl = new QCLabware("STAN-1", null, null, null, commentIds, scs);
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
        assertProblem(problems, expectedProblem);
        ArgumentCaptor<Stream<Integer>> idStreamCaptor = streamCaptor();
        verify(mockVal).checkCommentIds(idStreamCaptor.capture());
        assertThat(idStreamCaptor.getValue()).containsExactlyElementsOf(combinedCommentIds);
    }

    @Test
    public void testCheckSampleComments_valid() {
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Sample[] samples = EntityFactory.makeSamples(2);

        Labware[] lws = new Labware[2];
        lws[0] = EntityFactory.makeLabware(lt, samples);
        lws[1] = EntityFactory.makeEmptyLabware(lt);
        lws[1].getFirstSlot().addSample(samples[0]);
        lws[1].getFirstSlot().addSample(samples[1]);
        lws[0].setBarcode("STAN-10");
        lws[1].setBarcode("STAN-11");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        List<QCSampleComment> scs1 = List.of(
                new QCSampleComment(A1, samples[0].getId(), 3),
                new QCSampleComment(A2, samples[1].getId(), 4)
        );
        List<QCSampleComment> scs2 = List.of(
                new QCSampleComment(A1, samples[0].getId(), 5),
                new QCSampleComment(A1, samples[1].getId(), 6)
        );

        List<QCLabware> qcls = List.of(
                new QCLabware("STAN-10", null, null, null, null, scs1),
                new QCLabware("STAN-11", null, null, null, null, scs2),
                new QCLabware("STAN-12", null, null, null, null, List.of())
        );

        service.checkSampleComments(mockVal, qcls, lwMap);
        assertThat(problems).isEmpty();
    }

    @Test
    public void testCheckSampleComments_invalid() {
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Sample[] samples = EntityFactory.makeSamples(2);
        Labware lw = EntityFactory.makeLabware(lt, samples);
        lw.setBarcode("STAN-10");
        final Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3);
        List<QCSampleComment> scs = List.of(
                new QCSampleComment(null, 17, 18),
                new QCSampleComment(A3, 18, 19),
                new QCSampleComment(A2, 1, 20),
                new QCSampleComment(A1, null, 21)
        );
        QCLabware qcl = new QCLabware(lw.getBarcode(), null, null, null, null, scs);

        service.checkSampleComments(mockVal, List.of(qcl), UCMap.from(Labware::getBarcode, lw));

        assertThat(problems).containsExactlyInAnyOrder("Missing slot address for sample comment.",
                "No slot at address A3 in labware STAN-10.",
                "Missing sample ID for sample comment.",
                "Sample ID 1 is not present in slot A2 of labware STAN-10."
        );
    }

    @Test
    public void testCheckRunNames_none() {
        Labware lw = EntityFactory.getTube();
        List<QCLabware> qcls = List.of(
                new QCLabware(lw.getBarcode(), null, "SGP1", null, null, null)
        );
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        List<String> problems = new ArrayList<>(0);
        service.checkRunNames(problems, qcls, lwMap);
        verifyNoInteractions(mockLwNoteService);
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCheckRunNames(boolean valid) {
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0,3).mapToObj(i -> EntityFactory.makeEmptyLabware(lt, "STAN-"+i)).toArray(Labware[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        List<QCLabware> qcls = List.of(
                new QCLabware(lws[0].getBarcode(), "run1", null, null, null, null),
                new QCLabware(lws[1].getBarcode(), "run2", null, null, null, null),
                new QCLabware(lws[2].getBarcode(), null, null, null, null, null)
        );
        UCMap<Set<String>> bcValues = new UCMap<>(2);
        bcValues.put(lws[0].getBarcode(), Set.of("run1", "runA"));
        bcValues.put(lws[1].getBarcode(), Set.of("run2", "runB"));
        when(mockLwNoteService.findNoteValuesForLabware(anyCollection(), any())).thenReturn(bcValues);

        Set<Labware> lwSet;
        if (valid) {
            lwSet = Set.of(lws[0], lws[1]);
        } else {
            lwSet = Set.of(lws[0], lws[1], lws[2]);
            qcls.get(1).setRunName("runX");
            qcls.get(2).setRunName("runY");
        }

        List<String> problems = new ArrayList<>(valid ? 0 : 2);
        service.checkRunNames(problems, qcls, lwMap);
        verify(mockLwNoteService).findNoteValuesForLabware(lwSet, RUN_NAME);
        if (valid) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactlyInAnyOrder(
                    "runX is not a recorded run-name for labware STAN-1.",
                    "runY is not a recorded run-name for labware STAN-2."
            );
        }
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

        List<QCLabware> qcls = List.of(new QCLabware("STAN-1", null, "SGP1", ldt(1), List.of(1,2), null),
                new QCLabware("STAN-2", null, "SGP1", ldt(2), null, null),
                new QCLabware("STAN-3", null, null, null, List.of(3), null));
        Operation[] ops = IntStream.rangeClosed(1, lws.length)
                .mapToObj(i -> new Operation(i, opType, null, null, null))
                .toArray(Operation[]::new);
        for (int i = 0; i < lws.length; ++i) {
            when(mockOpService.createOperationInPlace(opType, user, lws[i], null, null)).thenReturn(ops[i]);
        }
        doNothing().when(service).linkWorks(any(), any(), any());
        doNothing().when(service).linkComments(any(), any(), any(), any());
        OperationResult opRes = new OperationResult(Arrays.asList(ops), Arrays.asList(lws));
        doNothing().when(service).saveNotes(any(), any(), any());
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
        verify(service).saveNotes(qcls, lwOps, lwMap);
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
                new QCLabware("STAN-1", null, "SGP1", null, null, null),
                new QCLabware("STAN-2", null, "SGP2", null, null, null),
                new QCLabware("STAN-3", null, "SGP1", null, null, null)
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
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware[] lws = IntStream.rangeClosed(21,23)
                .mapToObj(i -> {
                    Labware lw = EntityFactory.makeEmptyLabware(lt, "STAN-"+i);
                    lw.setId(i);
                    return lw;
                })
                .toArray(Labware[]::new);
        UCMap<Operation> lwOps = new UCMap<>(lws.length);
        IntStream.range(0, lws.length)
                .forEach(i -> lwOps.put(lws[i].getBarcode(), ops[i]));
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        final Address A1 = new Address(1,1), A2 = new Address(1,2);

        List<QCLabware> qcls = List.of(
                new QCLabware("STAN-21", null, null, null, List.of(1,2,3), null),
                new QCLabware("STAN-22", null, null, null, null, List.of(new QCSampleComment(A1, 10,1))),
                new QCLabware("STAN-23", null, null, null, List.of(2), List.of(new QCSampleComment(A2, 11, 2)))
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
                new OperationComment(null, comments[1], ops[2].getId(), null, null, lws[2].getId()),
                new OperationComment(null, comments[0], ops[1].getId(), 10, lws[1].getSlot(A1).getId(), null),
                new OperationComment(null, comments[1], ops[2].getId(), 11, lws[2].getSlot(A2).getId(), null)
        );
    }

    @Test
    public void testSaveNotes_none() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeEmptyLabware(lt, "STAN-"+i)).toArray(Labware[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        List<QCLabware> qcls = List.of(
                new QCLabware("STAN-0", null, null, null, null, null),
                new QCLabware("STAN-1", "", null, null, null, null)
        );
        UCMap<Operation> opsMap = new UCMap<>();
        service.saveNotes(qcls, opsMap, lwMap);
        verifyNoInteractions(mockLwNoteService);
    }

    @Test
    public void testSaveNotes() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0,3).mapToObj(i -> EntityFactory.makeEmptyLabware(lt, "STAN-"+i)).toArray(Labware[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        List<QCLabware> qcls = List.of(
                new QCLabware("STAN-0", "run0", null, null, null, null),
                new QCLabware("STAN-1", "run1", null, null, null, null),
                new QCLabware("STAN-2", null, null, null, null, null)
        );
        UCMap<Operation> ops = new UCMap<>(1);
        ops.put("STAN-0", new Operation());
        service.saveNotes(qcls, ops, lwMap);
        UCMap<String> noteValues = new UCMap<>(2);
        noteValues.put("STAN-0", "run0");
        noteValues.put("STAN-1", "run1");

        verify(mockLwNoteService).createNotes(RUN_NAME, lwMap, ops, noteValues);
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
                .map(lw -> new QCLabware(lw.getBarcode(), null, null, null, null, null))
                .collect(toList());

        OperationResult opres = service.assembleResult(qcls, lwOps, lwMap);
        assertThat(opres.getOperations()).containsExactly(ops);
        assertThat(opres.getLabware()).containsExactly(lws);
    }
}