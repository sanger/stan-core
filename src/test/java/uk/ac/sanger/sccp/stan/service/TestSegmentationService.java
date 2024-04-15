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
import uk.ac.sanger.sccp.stan.request.SegmentationRequest;
import uk.ac.sanger.sccp.stan.request.SegmentationRequest.SegmentationLabware;
import uk.ac.sanger.sccp.stan.service.SegmentationServiceImp.SegmentationData;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.stan.service.work.WorkService.WorkOp;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/** Test {@link SegmentationServiceImp} */
class TestSegmentationService {
    @Mock
    private Clock mockClock;
    @Mock
    private ValidationHelperFactory mockValHelperFactory;
    @Mock
    private OperationService mockOpService;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private OperationRepo mockOpRepo;
    @Mock
    private OperationCommentRepo mockOpComRepo;
    @Mock
    private LabwareNoteRepo mockNoteRepo;

    @InjectMocks
    private SegmentationServiceImp service;

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
    void testPerform_valid() {
        User user = EntityFactory.getUser();
        SegmentationRequest request = new SegmentationRequest("Bananas", List.of(new SegmentationLabware()));
        SegmentationData data = new SegmentationData(new LinkedHashSet<>());
        OperationResult opResult = new OperationResult(List.of(), List.of());
        doReturn(data).when(service).validate(any(), any());
        doReturn(opResult).when(service).record(any(), any(), any());

        assertSame(opResult, service.perform(user, request));

        verify(service).validate(user, request);
        verify(service).record(request.getLabware(), user, data);
    }

    @Test
    void testPerform_invalid() {
        User user = EntityFactory.getUser();
        SegmentationRequest request = new SegmentationRequest("Bananas", List.of(new SegmentationLabware()));
        SegmentationData data = new SegmentationData(new LinkedHashSet<>());
        data.problems.add("Bad bananas");
        doReturn(data).when(service).validate(any(), any());
        assertValidationException(() -> service.perform(user, request), List.of("Bad bananas"));

        verify(service).validate(user, request);
        verify(service, never()).record(any(), any(), any());
    }

    @Test
    void testValidate_valid() {
        ValidationHelper val = mock(ValidationHelper.class);
        when(mockValHelperFactory.getHelper()).thenReturn(val);
        final Set<String> problems = new LinkedHashSet<>();
        when(val.getProblems()).thenReturn(problems);
        User user = EntityFactory.getUser();
        SegmentationRequest request = new SegmentationRequest("Bananas", List.of(new SegmentationLabware()));
        OperationType opType = EntityFactory.makeOperationType("Bananas", null);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP11"));
        Map<Integer, Comment> commentMap = Map.of(100, new Comment(100, "com", "cat"));
        doReturn(opType).when(val).checkOpType(any(), any(), any(), any());
        doReturn(lwMap).when(service).loadLabware(any(), any());
        doReturn(workMap).when(service).loadWorks(any(), any());
        doReturn(commentMap).when(service).loadComments(any(), any());
        doNothing().when(service).checkCostings(any(), any());
        doNothing().when(service).checkTimestamps(any(), any(), any(), any());

        SegmentationData data = service.validate(user, request);
        assertThat(data.problems).isEmpty();
        assertSame(opType, data.opType);
        assertSame(lwMap, data.labware);
        assertSame(workMap, data.works);
        assertSame(commentMap, data.comments);

        //noinspection unchecked
        ArgumentCaptor<Predicate<OperationType>> predicateCaptor = ArgumentCaptor.forClass(Predicate.class);
        verify(val).checkOpType(eq("Bananas"), eq(EnumSet.of(OperationTypeFlag.IN_PLACE)), isNull(), predicateCaptor.capture());
        Predicate<OperationType> predicate = predicateCaptor.getValue();
        assertFalse(predicate.test(opType));
        opType.setName("Cell segmentation");
        assertTrue(predicate.test(opType));

        verify(service).loadLabware(val, request.getLabware());
        verify(service).loadWorks(val, request.getLabware());
        verify(service).loadComments(val, request.getLabware());
        verify(service).checkCostings(same(problems), same(request.getLabware()));
        verify(service).checkTimestamps(same(val), same(mockClock), same(request.getLabware()), same(data.labware));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testValidate_null(boolean userIsNull) {
        ValidationHelper val = mock(ValidationHelper.class);
        when(mockValHelperFactory.getHelper()).thenReturn(val);
        final Set<String> problems = new LinkedHashSet<>();
        when(val.getProblems()).thenReturn(problems);
        SegmentationData data = service.validate(userIsNull ? null : EntityFactory.getUser(), null);
        verify(val, never()).checkOpType(any(), any(), any(), any(), any());
        verify(service, never()).loadLabware(any(), any());
        verify(service, never()).loadWorks(any(), any());
        verify(service, never()).loadComments(any(), any());
        verify(service, never()).checkCostings(any(), any());
        verify(service, never()).checkTimestamps(any(), any(), any(), any());
        if (userIsNull) {
            assertThat(data.problems).containsExactlyInAnyOrder("No user supplied.", "No request supplied.");
        } else {
            assertThat(data.problems).containsExactly("No request supplied.");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testValidate_empty(boolean listIsNull) {
        ValidationHelper val = mock(ValidationHelper.class);
        when(mockValHelperFactory.getHelper()).thenReturn(val);
        final Set<String> problems = new LinkedHashSet<>();
        when(val.getProblems()).thenReturn(problems);
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        User user = EntityFactory.getUser();
        SegmentationRequest request = new SegmentationRequest("opname", listIsNull ? null : List.of());
        when(val.checkOpType(any(), any(), any(), any())).thenReturn(opType);

        SegmentationData data = service.validate(user, request);

        verify(val).checkOpType(eq("opname"), any(), any(), any());
        verify(service, never()).loadLabware(any(), any());
        verify(service, never()).loadWorks(any(), any());
        verify(service, never()).loadComments(any(), any());
        verify(service, never()).checkCostings(any(), any());
        verify(service, never()).checkTimestamps(any(), any(), any(), any());

        assertProblem(data.problems, "No labware specified.");
    }

    @Test
    void testValidate_invalid() {
        ValidationHelper val = mock(ValidationHelper.class);
        when(mockValHelperFactory.getHelper()).thenReturn(val);
        final Set<String> problems = new LinkedHashSet<>();
        when(val.getProblems()).thenReturn(problems);
        SegmentationRequest request = new SegmentationRequest("opname", List.of(new SegmentationLabware()));
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        when(val.checkOpType(any(), any(), any(), any())).thenReturn(opType);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        Map<Integer, Comment> commentMap = Map.of(100, new Comment(100, "com", "cat"));

        doAnswer(addProblem("Bad lw", lwMap)).when(service).loadLabware(any(), any());
        doAnswer(addProblem("Bad work", workMap)).when(service).loadWorks(any(), any());
        doAnswer(addProblem("Bad comment", commentMap)).when(service).loadComments(any(), any());
        mayAddProblem("Bad costing").when(service).checkCostings(any(), any());
        mayAddProblem("Bad time").when(service).checkTimestamps(any(), any(), any(), any());

        SegmentationData data = service.validate(null, request);
        verify(val).checkOpType(eq("opname"), any(), any(), any());
        List<SegmentationLabware> lwReqs = request.getLabware();
        verify(service).loadLabware(val, lwReqs);
        verify(service).loadWorks(val, lwReqs);
        verify(service).loadComments(val, lwReqs);
        verify(service).checkCostings(any(), same(lwReqs));
        verify(service).checkTimestamps(val, mockClock, lwReqs, lwMap);

        assertThat(data.problems).containsExactlyInAnyOrder(
                "No user supplied.", "Bad lw", "Bad work", "Bad comment", "Bad costing", "Bad time"
        );
    }

    @Test
    void testLoadLabware() {
        ValidationHelper val = mock(ValidationHelper.class);
        List<String> barcodes = Arrays.asList("STAN-1A", "STAN-1B", null);
        List<SegmentationLabware> lwReqs = barcodes.stream()
                        .map(bc -> {
                            SegmentationLabware lwReq = new SegmentationLabware();
                            lwReq.setBarcode(bc);
                            return lwReq;
                        }).toList();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        when(val.checkLabware(any())).thenReturn(lwMap);
        assertSame(lwMap, service.loadLabware(val, lwReqs));
        verify(val).checkLabware(barcodes);
    }

    @Test
    void testLoadWorks() {
        ValidationHelper val = mock(ValidationHelper.class);
        List<String> workNumbers = Arrays.asList("SGP1", "SGP2", null);
        List<SegmentationLabware> lwReqs = workNumbers.stream()
                .map(wn -> {
                    SegmentationLabware lwReq = new SegmentationLabware();
                    lwReq.setWorkNumber(wn);
                    return lwReq;
                }).toList();
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        when(val.checkWork(anyCollection())).thenReturn(workMap);
        assertSame(workMap, service.loadWorks(val, lwReqs));
        verify(val).checkWork(workNumbers);
    }

    @Test
    void testLoadComments() {
        ValidationHelper val = mock(ValidationHelper.class);
        List<SegmentationLabware> lwReqs = IntStream.range(0,3).mapToObj(i -> new SegmentationLabware()).toList();
        lwReqs.get(0).setCommentIds(List.of(1,2));
        lwReqs.get(1).setCommentIds(List.of(3));
        Map<Integer, Comment> commentMap = Map.of(100, new Comment(100, "com", "cat"));
        when(val.checkCommentIds(any())).thenReturn(commentMap);
        assertSame(commentMap, service.loadComments(val, lwReqs));
        ArgumentCaptor<Stream<Integer>> streamCaptor = streamCaptor();
        verify(val).checkCommentIds(streamCaptor.capture());
        assertThat(streamCaptor.getValue()).containsExactlyInAnyOrder(1,2,3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCheckCostings(boolean anyNull) {
        List<SegmentationLabware> lwReqs = IntStream.range(0,4).mapToObj(i -> new SegmentationLabware()).toList();
        lwReqs.get(0).setCosting(SlideCosting.Faculty);
        lwReqs.get(1).setCosting(SlideCosting.SGP);
        if (!anyNull) {
            lwReqs.get(2).setCosting(SlideCosting.Faculty);
            lwReqs.get(3).setCosting(SlideCosting.Faculty);
        }
        List<String> problems = new ArrayList<>(anyNull ? 1 : 0);
        service.checkCostings(problems, lwReqs);
        assertProblem(problems, anyNull ? "Costing missing from request." : null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCheckTimestamps(boolean withLw) {
        ValidationHelper val = mock(ValidationHelper.class);
        Clock clock = Clock.fixed(LocalDateTime.of(2024,4,10,12,0).toInstant(ZoneOffset.UTC), ZoneId.systemDefault());
        LocalDate today = LocalDate.of(2024,4,10);
        Labware lw = (withLw ? EntityFactory.getTube() : null);
        UCMap<Labware> lwMap = withLw ? UCMap.from(Labware::getBarcode, lw) : null;
        List<SegmentationLabware> lwReqs = List.of(new SegmentationLabware(), new SegmentationLabware(), new SegmentationLabware());
        lwReqs.get(0).setBarcode(withLw ? lw.getBarcode() : "STAN-1");
        lwReqs.get(0).setPerformed(LocalDateTime.of(2024,4,1,10,0));
        lwReqs.get(1).setPerformed(LocalDateTime.of(2024,4,2,10,0));

        service.checkTimestamps(val, clock, lwReqs, lwMap);

        verify(val).checkTimestamp(lwReqs.get(0).getPerformed(), today, withLw ? lw : null);
        verify(val).checkTimestamp(lwReqs.get(1).getPerformed(), today, null);
        verifyNoMoreInteractions(val);
    }

    @Test
    void testRecord() {
        final User user = EntityFactory.getUser();
        final Comment comment = new Comment(200, "com", "cat");
        SegmentationData data = new SegmentationData(List.of());
        Work work = EntityFactory.makeWork("SGP1");
        final LabwareType lt = EntityFactory.getTubeType();
        List<Labware> lws = IntStream.range(0,5).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).toList();
        data.labware = UCMap.from(lws, Labware::getBarcode);
        List<SegmentationLabware> lwReqs = lws.stream()
                .map(lw -> {
                    SegmentationLabware lwReq = new SegmentationLabware();
                    lwReq.setBarcode(lw.getBarcode());
                    return lwReq;
                })
                .toList();
        List<Operation> ops = IntStream.range(0, lwReqs.size())
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(100+i);
                    return op;
                })
                .toList();
        doAnswer(invocation -> {
            Labware lw = invocation.getArgument(3);
            int index = lws.indexOf(lw);
            Operation op = ops.get(index);
            if (index==0 || index==4) {
                List<Operation> opsToUpdate = invocation.getArgument(4);
                opsToUpdate.add(op);
            }
            if (index==1 || index==4) {
                List<LabwareNote> newNotes = invocation.getArgument(5);
                newNotes.add(new LabwareNote(null, lw.getId(), op.getId(), "costing", "Faculty"));
            }
            if (index==2 || index==4) {
                List<OperationComment> newOpComs = invocation.getArgument(6);
                newOpComs.add(new OperationComment(null, comment, op.getId(), 500, 600, null));
            }
            if (index==3 || index==4) {
                List<WorkOp> newWorkOps = invocation.getArgument(7);
                newWorkOps.add(new WorkOp(work, op));
            }
            return op;
        }).when(service).recordOp(any(), any(), any(), any(), any(), any(), any(), any());

        OperationResult opRes = service.record(lwReqs, user, data);

        verify(service, times(lws.size())).recordOp(any(), any(), any(), any(), any(), any(), any(), any());

        for (int i = 0; i < lws.size(); ++i) {
            verify(service).recordOp(same(user), same(data), same(lwReqs.get(i)), same(lws.get(i)),
                    any(), any(), any(), any());
        }

        verify(mockOpRepo).saveAll(List.of(ops.get(0), ops.get(4)));
        verify(mockNoteRepo).saveAll(List.of(new LabwareNote(null, lws.get(1).getId(), ops.get(1).getId(), "costing", "Faculty"),
                new LabwareNote(null, lws.get(4).getId(), ops.get(4).getId(), "costing", "Faculty")));
        verify(mockOpComRepo).saveAll(List.of(new OperationComment(null, comment, ops.get(2).getId(), 500, 600, null),
                new OperationComment(null, comment, ops.get(4).getId(), 500, 600, null)));
        ArgumentCaptor<Stream<WorkOp>> workOpCaptor = streamCaptor();
        verify(mockWorkService).linkWorkOps(workOpCaptor.capture());
        assertThat(workOpCaptor.getValue()).containsExactly(new WorkOp(work, ops.get(3)), new WorkOp(work, ops.get(4)));

        assertThat(opRes.getLabware()).containsExactlyElementsOf(lws);
        assertThat(opRes.getOperations()).containsExactlyElementsOf(ops);
    }

    @ParameterizedTest
    @CsvSource({
            "false,false,false,false",
            "false,true,false,true",
            "false,false,true,true",
            "true,true,false,false",
    })
    void testRecordOp(boolean hasTime, boolean hasCosting, boolean hasCommentIds, boolean hasWork) {
        SegmentationLabware lwReq = new SegmentationLabware();
        SegmentationData data = new SegmentationData(List.of());
        data.opType = EntityFactory.makeOperationType("opname", null);
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        lwReq.setBarcode(lw.getBarcode());
        List<Comment> comments;
        Work work;

        Operation op = new Operation();
        op.setId(500);
        op.setActions(List.of(new Action(501, 500, lw.getFirstSlot(), lw.getFirstSlot(), EntityFactory.getSample(), EntityFactory.getSample())));

        if (hasTime) {
            lwReq.setPerformed(LocalDateTime.of(2024,1,1,12,0));
        }
        if (hasCosting) {
            lwReq.setCosting(SlideCosting.Faculty);
        }
        if (hasCommentIds) {
            lwReq.setCommentIds(List.of(10,11));
            comments = List.of(new Comment(10, "com10", "cat"), new Comment(11, "com11", "cat"));
            data.comments = comments.stream().collect(inMap(Comment::getId));
        } else {
            comments = null;
        }
        if (hasWork) {
            lwReq.setWorkNumber("SGP1");
            work = EntityFactory.makeWork("SGP1");
            data.works = UCMap.from(Work::getWorkNumber, work);
        } else {
            work = null;
        }

        when(mockOpService.createOperationInPlace(data.opType, user, lw, null, null)).thenReturn(op);

        final List<Operation> opsToUpdate = new ArrayList<>();
        final List<LabwareNote> newNotes = new ArrayList<>();
        final List<OperationComment> newOpComs = new ArrayList<>();
        final List<WorkOp> newWorkOps = new ArrayList<>();
        assertSame(op, service.recordOp(user, data, lwReq, lw, opsToUpdate, newNotes, newOpComs, newWorkOps));

        verify(mockOpService).createOperationInPlace(data.opType, user, lw, null, null);

        assertMayContain(opsToUpdate, hasTime ? op : null);
        assertMayContain(newNotes, hasCosting ? new LabwareNote(null, lw.getId(), op.getId(), "costing", "Faculty") : null);
        assertMayContain(newWorkOps, hasWork ? new WorkOp(work, op) : null);
        if (hasCommentIds) {
            final int slotId = lw.getFirstSlot().getId();
            final int sampleId = EntityFactory.getSample().getId();
            List<OperationComment> expectedOpComs = comments.stream()
                    .map(com -> new OperationComment(null, com, op.getId(), sampleId, slotId, null))
                    .toList();
            assertThat(newOpComs).containsExactlyInAnyOrderElementsOf(expectedOpComs);
        } else {
            assertThat(newOpComs).isEmpty();
        }
    }

    private static <E> void assertMayContain(Collection<E> cl, E item) {
        if (item==null) {
            assertThat(cl).isEmpty();
        } else {
            assertThat(cl).containsExactly(item);
        }
    }
}