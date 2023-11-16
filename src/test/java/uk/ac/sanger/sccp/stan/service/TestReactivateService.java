package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ReactivateLabware;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

class TestReactivateService {
    @Mock
    private LabwareValidatorFactory mockLwValFactory;
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private OperationCommentRepo mockOpComRepo;
    @Mock
    private CommentValidationService mockCommentValidationService;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private OperationService mockOpService;

    @InjectMocks
    private ReactivateServiceImp service;

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
    void testReactivate_missingFields() {
        assertValidationException(() -> service.reactivate(null, null), List.of(
                "No user specified.", "No labware specified."
        ));
        verify(service, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testReactivate_invalid() {
        User user = EntityFactory.getUser();
        List<ReactivateLabware> items = List.of(new ReactivateLabware());
        doAnswer(addProblem("Bad lw")).when(service).loadLabware(any(), same(items));
        doAnswer(addProblem("Bad work")).when(service).loadWork(any(), same(items));
        doAnswer(addProblem("Bad comment")).when(service).loadComments(any(), same(items));
        doAnswer(addProblem("Bad op type")).when(service).loadOpType(any());

        assertValidationException(() -> service.reactivate(user, items), List.of(
                "Bad lw", "Bad work", "Bad comment", "Bad op type"
        ));
        verify(service, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testReactivate_valid() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Reactivate", null, OperationTypeFlag.IN_PLACE);
        List<ReactivateLabware> items = List.of(new ReactivateLabware());
        final Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        Map<Integer, Comment> commentMap = Map.of(100, new Comment(100, "oops", "bananas"));
        OperationResult opres = new OperationResult(List.of(new Operation()), List.of(lw));

        doReturn(lwMap).when(service).loadLabware(any(), any());
        doReturn(workMap).when(service).loadWork(any(), any());
        doReturn(commentMap).when(service).loadComments(any(), any());
        doReturn(opType).when(service).loadOpType(any());
        doReturn(opres).when(service).record(any(), any(), any(), any(), any(), any());

        assertSame(opres, service.reactivate(user, items));

        verify(service).loadLabware(any(), same(items));
        verify(service).loadWork(any(), same(items));
        verify(service).loadComments(any(), same(items));
        verify(service).loadOpType(any());
        verify(service).record(opType, user, items, lwMap, workMap, commentMap);
    }

    @ParameterizedTest
    @MethodSource("loadLabwareArgs")
    void testLoadLabware(List<String> barcodes, List<Labware> foundLabware, String valProblem, String expectedProblem) {
        List<ReactivateLabware> items = barcodes.stream()
                .map(bc -> new ReactivateLabware(bc, null, null))
                .collect(toList());
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        when(val.getLabware()).thenReturn(foundLabware);
        when(val.getErrors()).thenReturn(valProblem==null ? List.of() : List.of(valProblem));

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        UCMap<Labware> lwMap = service.loadLabware(problems, items);

        assertProblem(problems, expectedProblem);
        assertThat(lwMap).hasSameSizeAs(foundLabware);
        for (Labware lw : foundLabware) {
            assertSame(lw, lwMap.get(lw.getBarcode()));
        }

        verify(val).loadLabware(mockLwRepo, barcodes);
        verify(val).validateUnique();
        verify(val).validateNonEmpty();
    }

    static Stream<Arguments> loadLabwareArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        Labware discardedLw = EntityFactory.makeLabware(lt, sample);
        discardedLw.setDiscarded(true);
        Labware destroyedLw = EntityFactory.makeLabware(lt, sample);
        destroyedLw.setDestroyed(true);
        Labware activeLw = EntityFactory.makeLabware(lt, sample);

        discardedLw.setBarcode("STAN-DISC");
        destroyedLw.setBarcode("STAN-DEST");
        activeLw.setBarcode("STAN-A");

        return Arrays.stream(new Object[][] {
                {List.of("STAN-DISC", "STAN-DEST"), List.of(discardedLw, destroyedLw), null, null},
                {List.of("STAN-DISC", "STAN-DEST", "bananas"), List.of(discardedLw, destroyedLw), "Bad bananas", "Bad bananas"},
                {List.of("STAN-DISC", "STAN-A"), List.of(discardedLw, activeLw), null, "Labware is not discarded or destroyed: [STAN-A]"},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLoadWork(boolean anyProblem) {
        String expectedProblem = anyProblem ? "Bad work" : null;
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        mayAddProblem(expectedProblem, workMap).when(mockWorkService).validateUsableWorks(any(), any());
        List<String> workNumbers = List.of("SGP1", "SGP2");
        List<ReactivateLabware> items = workNumbers.stream().map(wn -> new ReactivateLabware(null, wn, null)).collect(toList());
        final List<String> problems = new ArrayList<>(anyProblem ? 1 : 0);
        assertSame(workMap, service.loadWork(problems, items));
        assertProblem(problems, expectedProblem);
        verify(mockWorkService).validateUsableWorks(any(), eq(workNumbers));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLoadComments(boolean anyProblem) {
        String expectedProblem = anyProblem ? "Bad comment" : null;
        Comment comment = new Comment(100, "alpha", "beta");
        mayAddProblem(expectedProblem, List.of(comment)).when(mockCommentValidationService).validateCommentIds(any(), any());
        List<Integer> commentIds = List.of(100,101);
        List<ReactivateLabware> items = commentIds.stream().map(id -> new ReactivateLabware(null, null, id)).collect(toList());
        final List<String> problems = new ArrayList<>(anyProblem ? 1 : 0);
        Map<Integer, Comment> commentMap = service.loadComments(problems, items);
        assertThat(commentMap).hasSize(1).containsEntry(comment.getId(), comment);
        assertProblem(problems, expectedProblem);
        ArgumentCaptor<Stream<Integer>> streamCaptor = streamCaptor();
        verify(mockCommentValidationService).validateCommentIds(any(), streamCaptor.capture());
        assertThat(streamCaptor.getValue()).containsExactlyElementsOf(commentIds);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLoadOpType(boolean found) {
        OperationType opType = found ? EntityFactory.makeOperationType("Reactivate", null, OperationTypeFlag.IN_PLACE) : null;
        when(mockOpTypeRepo.findByName("Reactivate")).thenReturn(Optional.ofNullable(opType));
        final List<String> problems = new ArrayList<>(found ? 0 : 1);
        assertSame(opType, service.loadOpType(problems));
        assertProblem(problems, found ? null : "Operation type \"Reactivate\" not found.");
    }

    @Test
    void testRecord() {
        Operation[] ops = IntStream.range(1,3).mapToObj(i -> {
            Operation op = new Operation();
            op.setId(i);
            return op;
        }).toArray(Operation[]::new);
        doNothing().when(service).updateLabware(any());
        doReturn(ops[0], ops[1]).when(mockOpService).createOperationInPlace(any(), any(), any(), any(), any());
        doNothing().when(service).recordComment(any(), any());
        doReturn(null).when(mockWorkService).linkWorkOps(any());

        OperationType opType = EntityFactory.makeOperationType("Reactivate", null, OperationTypeFlag.IN_PLACE);
        User user = EntityFactory.getUser();
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = {EntityFactory.makeEmptyLabware(lt, "STAN-1"), EntityFactory.makeEmptyLabware(lt, "STAN-2")};
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        Work[] works = EntityFactory.makeWorks("SGP1", "SGP2");
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, works);
        Comment[] comments = { new Comment(20, "alpha", "banana"), new Comment(21, "beta", "banana")};
        Map<Integer, Comment> commentMap = Arrays.stream(comments).collect(inMap(Comment::getId));

        List<ReactivateLabware> items = IntStream.range(0, lws.length)
                .mapToObj(i -> new ReactivateLabware(lws[i].getBarcode(), works[i].getWorkNumber(), comments[i].getId()))
                .collect(toList());

        OperationResult opres = service.record(opType, user, items, lwMap, workMap, commentMap);

        for (int i = 0; i < lws.length; ++i) {
            verify(mockOpService).createOperationInPlace(opType, user, lws[i], null, null);
            verify(service).recordComment(ops[i], comments[i]);
        }

        WorkService.WorkOp[] expectedWorkOps = IntStream.range(0, ops.length)
                .mapToObj(i -> new WorkService.WorkOp(works[i], ops[i]))
                .toArray(WorkService.WorkOp[]::new);

        ArgumentCaptor<Stream<WorkService.WorkOp>> streamCaptor = streamCaptor();
        verify(mockWorkService).linkWorkOps(streamCaptor.capture());
        assertThat(streamCaptor.getValue()).containsExactly(expectedWorkOps);

        assertThat(opres.getOperations()).containsExactly(ops);
        assertThat(opres.getLabware()).containsExactly(lws);
    }

    @Test
    void testUpdateLabware() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt, "STAN-1");
        Labware lw2 = EntityFactory.makeEmptyLabware(lt, "STAN-2");
        lw1.setDiscarded(true);
        lw2.setDestroyed(true);
        List<Labware> lws = List.of(lw1, lw2);
        service.updateLabware(lws);
        assertThat(lws).noneMatch(((Predicate<Labware>) Labware::isDestroyed).or(Labware::isDiscarded));
        verify(mockLwRepo).saveAll(lws);
    }

    @Test
    void testRecordComment() {
        OperationType opType = EntityFactory.makeOperationType("Reactivate", null);
        Sample sample1 = EntityFactory.getSample();
        Sample sample2 = new Sample(sample1.getId()+1, null, sample1.getTissue(), sample1.getBioState());
        Sample[] samples = {sample1, sample2};
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware lw = EntityFactory.makeLabware(lt, samples);
        Comment comment = new Comment(100, "Alpha", "Beta");
        List<Slot> slots = lw.getSlots();
        Operation op = EntityFactory.makeOpForSlots(opType, slots, slots, null);
        Integer opId = op.getId();
        service.recordComment(op, comment);
        List<OperationComment> expectedOpComs = IntStream.range(0, samples.length)
                .mapToObj(i -> new OperationComment(null, comment, opId, samples[i].getId(), slots.get(i).getId(), null))
                .collect(toList());
        verify(mockOpComRepo).saveAll(expectedOpComs);
    }
}