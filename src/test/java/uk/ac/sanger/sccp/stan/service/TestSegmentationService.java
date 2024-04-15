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

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.stan.service.SegmentationServiceImp.CELL_SEGMENTATION_OP_NAME;
import static uk.ac.sanger.sccp.stan.service.SegmentationServiceImp.QC_OP_NAME;
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
    private OperationTypeRepo mockOpTypeRepo;
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
        UCMap<LocalDateTime> priorOpMap = new UCMap<>();
        priorOpMap.put("somebc", LocalDateTime.now());
        doReturn(opType).when(val).checkOpType(any(), any(), any(), any());
        doReturn(lwMap).when(service).loadLabware(any(), any());
        doReturn(workMap).when(service).loadWorks(any(), any());
        doReturn(commentMap).when(service).loadComments(any(), any());
        doReturn(priorOpMap).when(service).checkPriorOps(any(), any(), any());
        doNothing().when(service).checkCostings(any(), any(), any());

        doNothing().when(service).checkTimestamps(any(), any(), any(), any(), any());

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
        verify(service).checkCostings(same(problems), same(opType), same(request.getLabware()));
        verify(service).checkPriorOps(same(problems), same(opType), eq(lwMap.values()));
        verify(service).checkTimestamps(same(val), same(mockClock), same(request.getLabware()), same(data.labware), same(priorOpMap));
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
        verify(service, never()).checkCostings(any(), any(), any());
        verify(service, never()).checkPriorOps(any(), any(), any());
        verify(service, never()).checkTimestamps(any(), any(), any(), any(), any());
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
        verify(service, never()).checkCostings(any(), any(), any());
        verify(service, never()).checkPriorOps(any(), any(), any());
        verify(service, never()).checkTimestamps(any(), any(), any(), any(), any());

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
        UCMap<LocalDateTime> priorOps = new UCMap<>();
        priorOps.put("somebc", LocalDateTime.now());

        doAnswer(addProblem("Bad lw", lwMap)).when(service).loadLabware(any(), any());
        doAnswer(addProblem("Bad work", workMap)).when(service).loadWorks(any(), any());
        doAnswer(addProblem("Bad comment", commentMap)).when(service).loadComments(any(), any());
        mayAddProblem("Bad costing").when(service).checkCostings(any(), any(), any());
        doAnswer(addProblem("Bad prior op", priorOps)).when(service).checkPriorOps(any(), any(), any());
        mayAddProblem("Bad time").when(service).checkTimestamps(any(), any(), any(), any(), any());

        SegmentationData data = service.validate(null, request);
        verify(val).checkOpType(eq("opname"), any(), any(), any());
        List<SegmentationLabware> lwReqs = request.getLabware();
        verify(service).loadLabware(val, lwReqs);
        verify(service).loadWorks(val, lwReqs);
        verify(service).loadComments(val, lwReqs);
        verify(service).checkCostings(any(), same(opType), same(lwReqs));
        verify(service).checkTimestamps(val, mockClock, lwReqs, lwMap, priorOps);

        assertThat(data.problems).containsExactlyInAnyOrder(
                "No user supplied.", "Bad lw", "Bad work", "Bad comment", "Bad costing", "Bad prior op", "Bad time"
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
    void testCheckCostings_segmentation(boolean anyNull) {
        List<SegmentationLabware> lwReqs = IntStream.range(0,4).mapToObj(i -> new SegmentationLabware()).toList();
        OperationType opType = EntityFactory.makeOperationType(CELL_SEGMENTATION_OP_NAME, null, OperationTypeFlag.IN_PLACE);
        lwReqs.get(0).setCosting(SlideCosting.Faculty);
        lwReqs.get(1).setCosting(SlideCosting.SGP);
        if (!anyNull) {
            lwReqs.get(2).setCosting(SlideCosting.Faculty);
            lwReqs.get(3).setCosting(SlideCosting.Faculty);
        }
        List<String> problems = new ArrayList<>(anyNull ? 1 : 0);
        service.checkCostings(problems, opType, lwReqs);
        assertProblem(problems, anyNull ? "Costing missing from request." : null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCheckCostings_qc(boolean anyPresent) {
        List<SegmentationLabware> lwReqs = IntStream.range(0,4).mapToObj(i -> new SegmentationLabware()).toList();
        OperationType opType = EntityFactory.makeOperationType(QC_OP_NAME, null, OperationTypeFlag.IN_PLACE);
        if (anyPresent) {
            lwReqs.get(2).setCosting(SlideCosting.Faculty);
            lwReqs.get(3).setCosting(SlideCosting.SGP);
        }
        List<String> problems = new ArrayList<>(anyPresent ? 1 : 0);
        service.checkCostings(problems, opType, lwReqs);
        assertProblem(problems, anyPresent ? "Costing not expected in this request." : null);
    }

    @Test
    void testCheckCosting_noOpType() {
        List<SegmentationLabware> lwReqs = List.of(new SegmentationLabware());
        List<String> problems = new ArrayList<>(0);
        service.checkCostings(problems, null, lwReqs);
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2})
    void testCheckPriorOps_null(int mode) {
        // 0: No op type
        // 1: Op type isn't QC
        // 2: No labware
        OperationType opType = (mode==0 ? null : EntityFactory.makeOperationType(mode==1 ? "Bananas" : QC_OP_NAME, null, OperationTypeFlag.IN_PLACE));
        List<Labware> lws = (mode==2 ? null : List.of(EntityFactory.getTube()));
        List<String> problems = new ArrayList<>(0);
        assertNull(service.checkPriorOps(problems, opType, lws));
        verifyNoInteractions(mockOpTypeRepo);
        verifyNoInteractions(mockOpRepo);
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCheckPriorOps(boolean anyMissing) {
        OperationType opType = EntityFactory.makeOperationType(QC_OP_NAME, null, OperationTypeFlag.IN_PLACE);
        OperationType priorOpType = EntityFactory.makeOperationType(CELL_SEGMENTATION_OP_NAME, null, OperationTypeFlag.IN_PLACE);
        when(mockOpTypeRepo.getByName(CELL_SEGMENTATION_OP_NAME)).thenReturn(priorOpType);
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        List<Labware> lws = IntStream.range(0,3)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toList();
        Set<Integer> lwIds = lws.stream().map(Labware::getId).collect(toSet());
        List<LocalDateTime> times = IntStream.rangeClosed(1, anyMissing ? 2 : 3)
                .mapToObj(i -> LocalDateTime.of(2024,1,i,12,0))
                .toList();
        List<Operation> ops = IntStream.range(0, times.size())
                .mapToObj(i -> {
                    List<Labware> opLw = List.of(lws.get(i));
                    Operation op = EntityFactory.makeOpForLabware(priorOpType, opLw, opLw);
                    op.setPerformed(times.get(i));
                    return op;
                }).toList();
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(priorOpType, lwIds)).thenReturn(ops);

        List<String> problems = new ArrayList<>(anyMissing ? 1 : 0);
        UCMap<LocalDateTime> timeMap = service.checkPriorOps(problems, opType, lws);
        verify(mockOpTypeRepo).getByName(CELL_SEGMENTATION_OP_NAME);
        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(priorOpType, lwIds);

        assertProblem(problems,
                anyMissing ? "Cell segmentation has not been recorded on labware ["+lws.get(2).getBarcode()+"]." : null);
        assertThat(timeMap).hasSize(times.size());
        for (int i = 0; i < times.size(); ++i) {
            assertEquals(times.get(i), timeMap.get(lws.get(i).getBarcode()));
        }
    }

    @Test
    void testGreater() {
        for (Object[] args : new Object[][]{
                {null,null,false},
                {null,5,false},
                {null,-5,false},
                {1,null,true},
                {-4,null,true},
                {2,3,false},
                {5,2,true},
        }) {
            Integer a = (Integer) args[0];
            Integer b = (Integer) args[1];
            assertEquals(args[2], SegmentationServiceImp.greater(a, b));
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "true,true"})
    void testCheckTimestamps(boolean withLw, boolean withTimes) {
        ValidationHelper val = mock(ValidationHelper.class);
        Clock clock = Clock.fixed(LocalDateTime.of(2024,4,10,12,0).toInstant(ZoneOffset.UTC), ZoneId.systemDefault());
        LocalDate today = LocalDate.of(2024,4,10);
        Labware lw = (withLw ? EntityFactory.getTube() : null);
        UCMap<Labware> lwMap = withLw ? UCMap.from(Labware::getBarcode, lw) : null;
        List<SegmentationLabware> lwReqs = List.of(new SegmentationLabware(), new SegmentationLabware(), new SegmentationLabware());
        lwReqs.get(0).setBarcode(withLw ? lw.getBarcode() : "STAN-1");
        lwReqs.get(0).setPerformed(LocalDateTime.of(2024,4,1,10,0));
        lwReqs.get(1).setPerformed(LocalDateTime.of(2024,4,2,10,0));

        UCMap<LocalDateTime> priorOpTimes;
        LocalDateTime priorOpTime;
        if (withTimes && lw!=null) {
            priorOpTime = LocalDateTime.of(2024,1,1,12,0);
            priorOpTimes = new UCMap<>();
            priorOpTimes.put(lw.getBarcode(), priorOpTime);
        } else {
            priorOpTime = null;
            priorOpTimes = null;
        }

        service.checkTimestamps(val, clock, lwReqs, lwMap, priorOpTimes);

        verify(val).checkTimestamp(lwReqs.get(0).getPerformed(), today, withLw ? lw : null, priorOpTime);
        verify(val).checkTimestamp(lwReqs.get(1).getPerformed(), today, (Labware) null, null);
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