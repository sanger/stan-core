package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ResultRequest;
import uk.ac.sanger.sccp.stan.request.ResultRequest.LabwareResult;
import uk.ac.sanger.sccp.stan.request.ResultRequest.SampleResult;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ResultServiceImp}
 * @author dr6
 */
public class TestResultService {
    private OperationTypeRepo mockOpTypeRepo;
    private LabwareRepo mockLwRepo;
    private OperationRepo mockOpRepo;
    private OperationCommentRepo mockOpCommentRepo;
    private ResultOpRepo mockResOpRepo;
    private LabwareValidatorFactory mockLabwareValidatorFactory;
    private OperationService mockOpService;
    private WorkService mockWorkService;
    private CommentValidationService mockCommentValidationService;

    private ResultServiceImp service;

    @BeforeEach
    void setup() {
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockOpRepo = mock(OperationRepo.class);
        mockOpCommentRepo = mock(OperationCommentRepo.class);
        mockResOpRepo = mock(ResultOpRepo.class);
        mockLabwareValidatorFactory = mock(LabwareValidatorFactory.class);
        mockOpService = mock(OperationService.class);
        mockWorkService = mock(WorkService.class);
        mockCommentValidationService = mock(CommentValidationService.class);

        service = spy(new ResultServiceImp(mockOpTypeRepo, mockLwRepo, mockOpRepo, mockOpCommentRepo,
                mockResOpRepo, mockLabwareValidatorFactory,
                mockOpService, mockWorkService, mockCommentValidationService));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testRecordResultForOperation(boolean valid) {
        OperationType resultOpType = EntityFactory.makeOperationType("Result op", null,
                OperationTypeFlag.IN_PLACE, OperationTypeFlag.RESULT);
        OperationType setupOpType = EntityFactory.makeOperationType("Setup op", null, OperationTypeFlag.IN_PLACE);
        doReturn(resultOpType).when(service).loadOpType(any(), eq(resultOpType.getName()));
        doReturn(setupOpType).when(service).loadOpType(any(), eq(setupOpType.getName()));
        Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        doReturn(lwMap).when(service).validateLabware(any(), any());
        doNothing().when(service).validateLabwareContents(any(), any(), any());
        Work work = new Work(200, "SGP200", null, null, null, Work.Status.active);
        doReturn(work).when(mockWorkService).validateUsableWork(any(), any());
        Map<Integer, Integer> lwStainMap = Map.of(100,200);
        final String stainMapProblem = "Stain map problem";
        if (valid) {
            doReturn(lwStainMap).when(service).lookUpPrecedingOps(any(), any(), any());
        } else {
            doAnswer(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add(stainMapProblem);
                return lwStainMap;
            }).when(service).lookUpPrecedingOps(any(), any(), any());
        }
        Map<Integer, Comment> commentMap = Map.of(17, new Comment());
        doReturn(commentMap).when(service).validateComments(any(), any());

        final ResultRequest request = new ResultRequest();
        request.setWorkNumber(work.getWorkNumber());
        request.setLabwareResults(List.of(new LabwareResult(lw.getBarcode(), List.of(new SampleResult()))));
        request.setOperationType(resultOpType.getName());
        OperationResult opRes = new OperationResult(List.of(), List.of(lw));
        doReturn(opRes).when(service).createResults(any(), any(), any(), any(), any(), any(), any());
        final List<LabwareResult> lrs = request.getLabwareResults();
        if (valid) {
            User user = EntityFactory.getUser();
            assertSame(opRes, service.recordResultForOperation(user, request, setupOpType.getName()));
            verify(service).createResults(user, resultOpType, lrs, lwMap, lwStainMap, commentMap, work);
        } else {
            ValidationException exc = assertThrows(ValidationException.class, () -> service.recordResultForOperation(null, request, setupOpType.getName()));
            assertThat(exc).hasMessage("The result request could not be validated.");
            //noinspection unchecked
            assertThat((Collection<Object>) exc.getProblems()).containsExactlyInAnyOrder(stainMapProblem, "No user specified.");
            verify(service, never()).createResults(any(), any(), any(), any(), any(), any(), any());
        }

        verify(service).loadOpType(anyCollection(), eq(resultOpType.getName()));
        verify(service).loadOpType(anyCollection(), eq(setupOpType.getName()));
        verify(service).validateLabware(anyCollection(), eq(lrs));
        verify(service).validateLabwareContents(anyCollection(), eq(lwMap), eq(lrs));
        verify(service).validateComments(anyCollection(), eq(lrs));
        verify(mockWorkService).validateUsableWork(anyCollection(), eq(request.getWorkNumber()));
        verify(service).lookUpPrecedingOps(anyCollection(), eq(setupOpType), eq(lwMap.values()));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testLoadOpType(boolean valid) {
        final String opTypeName = "Bananas";
        OperationType opType = (valid ? new OperationType(2, opTypeName) : null);
        when(mockOpTypeRepo.findByName(opTypeName)).thenReturn(Optional.ofNullable(opType));
        final List<String> problems = new ArrayList<>(valid ? 0 : 1);
        assertSame(opType, service.loadOpType(problems, opTypeName));
        if (valid) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly("Unknown operation type: \"Bananas\"");
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testValidateLabware() {
        LabwareValidator mockVal = mock(LabwareValidator.class);
        List<String> valErrors = List.of("Error 1", "Error 2");
        Labware lw = EntityFactory.getTube();
        when(mockVal.getErrors()).thenReturn(valErrors);
        when(mockVal.getLabware()).thenReturn(List.of(lw));
        when(mockLabwareValidatorFactory.getValidator()).thenReturn(mockVal);

        List<String> problems = new ArrayList<>(2);
        List<LabwareResult> lrs = List.of(
                new LabwareResult("STAN-001", List.of()),
                new LabwareResult("STAN-002", List.of()),
                new LabwareResult("STAN-001", List.of())
        );
        UCMap<Labware> result = service.validateLabware(problems, lrs);

        InOrder order = Mockito.inOrder(mockVal);
        order.verify(mockVal).loadLabware(mockLwRepo, List.of("STAN-001", "STAN-002", "STAN-001"));
        order.verify(mockVal).getErrors();
        order.verify(mockVal).getLabware();

        assertThat(result).hasSize(1).containsEntry(lw.getBarcode(), lw);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(valErrors);
    }

    @Test
    public void testValidateLabwareContents() {
        Labware lw0 = EntityFactory.getTube();
        Labware lw1 = EntityFactory.makeEmptyLabware(lw0.getLabwareType());
        Labware lw2 = EntityFactory.makeEmptyLabware(lw0.getLabwareType());
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw0, lw1, lw2);

        final Address B2 = new Address(2, 2);
        List<LabwareResult> lrs = List.of(
                new LabwareResult("No such barcode", List.of()),
                new LabwareResult(lw0.getBarcode(), List.of()),
                new LabwareResult(lw1.getBarcode(), List.of(
                        new SampleResult(new Address(1,1), PassFail.pass, null),
                        new SampleResult(new Address(1,2), PassFail.fail, 10))
                ),
                new LabwareResult(lw2.getBarcode(), List.of(
                        new SampleResult(new Address(2,1), PassFail.pass, null),
                        new SampleResult(B2, PassFail.fail, null))
                )
        );
        final String slotSampleProblem = "Slot sample problem.";

        doAnswer(invocation -> {
            Set<Integer> slotIds = invocation.getArgument(2);
            SampleResult sr = invocation.getArgument(3);
            if (sr.getAddress().equals(B2)) {
                Collection<String> prob = invocation.getArgument(0);
                prob.add(slotSampleProblem);
            }
            Address ad = sr.getAddress();
            slotIds.add(10 * ad.getRow() + ad.getColumn());
            return null;
        }).when(service).validateSampleResult(anyCollection(), any(), any(), any());

        final List<String> problems = new ArrayList<>();
        service.validateLabwareContents(problems, lwMap, lrs);
        assertThat(problems).containsExactlyInAnyOrder(slotSampleProblem, "No results specified for labware "+lw0.getBarcode()+".");

        //noinspection unchecked
        ArgumentCaptor<Set<Integer>> argCap = ArgumentCaptor.forClass(Set.class);
        for (LabwareResult lr : lrs) {
            for (SampleResult sr : lr.getSampleResults()) {
                verify(service).validateSampleResult(same(problems), same(lwMap.get(lr.getBarcode())), argCap.capture(), same(sr));
            }
        }
        // verify that the same slot/sample id sets were used for sample results on the same labware
        List<Set<Integer>> slotIdSets = argCap.getAllValues();
        assertSame(slotIdSets.get(0), slotIdSets.get(1));
        assertSame(slotIdSets.get(2), slotIdSets.get(3));
        assertEquals(Set.of(11,12), slotIdSets.get(0));
        assertEquals(Set.of(21,22), slotIdSets.get(2));
    }

    @ParameterizedTest
    @MethodSource("validateSampleResultArgs")
    public void testValidateSampleResult(SampleResult sr, Labware lw, Slot slot, Integer priorSlotId,
                                         Object expectedProblemsObj) {
        if (slot==null) {
            doAnswer(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add("No such slot.");
                return null;
            }).when(service).getNonemptySlot(any(), any(), any());
        } else if (slot.getSamples().isEmpty()) {
            doAnswer(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add("Slot is empty.");
                return null;
            }).when(service).getNonemptySlot(any(), any(), any());
        } else {
            doReturn(slot).when(service).getNonemptySlot(any(), any(), any());
        }
        Set<Integer> slotIds = new HashSet<>(2);
        if (priorSlotId != null) {
            slotIds.add(priorSlotId);
        }
        Collection<String> expectedProblems;
        if (expectedProblemsObj instanceof String) {
            expectedProblems = List.of((String) expectedProblemsObj);
        } else if (expectedProblemsObj==null) {
            expectedProblems = List.of();
        } else {
            //noinspection unchecked
            expectedProblems = (Collection<String>) expectedProblemsObj;
        }
        final List<String> problems = new ArrayList<>(expectedProblems.size());

        service.validateSampleResult(problems, lw, slotIds, sr);

        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        if (sr.getAddress()!=null) {
            verify(service).getNonemptySlot(problems, lw, sr.getAddress());
        }
    }

    static Stream<Arguments> validateSampleResultArgs() {
        Sample sam = EntityFactory.getSample();
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw = EntityFactory.makeLabware(lt, sam);
        lw.setBarcode("STAN-001");
        Address A1 = new Address(1,1);
        Address A2 = new Address(1,2);
        Slot slot = lw.getSlot(A1);
        Slot emptySlot = lw.getSlot(A2);

        final SampleResult passResult = new SampleResult(A1, PassFail.pass, null);
        final SampleResult failResult = new SampleResult(A1, PassFail.fail, 6);
        // sr, slot, sample, ssid, problems
        return Arrays.stream(new Object[][] {
                {passResult, slot, null, null},
                {failResult, slot, null, null},
                {passResult, slot, slot.getId()+1, null},

                {new SampleResult(A1, null, null), slot, null,
                        "Sample result is missing a result."},
                {new SampleResult(null, PassFail.pass, null), null, null,
                        "Sample result is missing a slot address."},
                {new SampleResult(A2, PassFail.pass, null), emptySlot, null,
                        "Slot is empty."},
                {new SampleResult(A1, PassFail.fail, null), slot, null,
                        "Missing comment ID for a fail result."},
                {new SampleResult(), null, null,
                        List.of("Sample result is missing a result.", "Sample result is missing a slot address.")},

                {passResult, null, null, "No such slot."},

                {passResult, slot, slot.getId(), "Multiple results specified for slot A1 in labware STAN-001."},
                {new SampleResult(A1, null, null), null, null,
                        List.of("Sample result is missing a result.", "No such slot.") },
        }).map(arr -> Arguments.of(arr[0], lw, arr[1], arr[2], arr[3]));
    }

    @ParameterizedTest
    @CsvSource(value={
            "false,false",
            "true,false",
            "true,true",
    })
    public void testGetNonemptySlot(boolean found, boolean hasSamples) {
        Labware lw;
        if (found && !hasSamples) {
            lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        } else {
            lw = EntityFactory.getTube();
        }
        List<String> problems = new ArrayList<>(found ? 0 : 1);
        if (!found) {
            assertNull(service.getNonemptySlot(problems, lw, new Address(1,2)));
            assertThat(problems).containsExactly("No slot in labware "+lw.getBarcode()+" has address A2.");
        } else if (!hasSamples) {
            assertNull(service.getNonemptySlot(problems, lw, new Address(1,1)));
            assertThat(problems).containsExactly("There are no samples in slot A1 of labware "+lw.getBarcode()+".");
        } else {
            assertSame(lw.getFirstSlot(), service.getNonemptySlot(problems, lw, new Address(1,1)));
            assertThat(problems).isEmpty();
        }
    }

    @Test
    public void testValidateComments() {
        List<Comment> commentList = List.of(
                new Comment(17, "Beep", "cats"),
                new Comment(18, "Boop", "cats")
        );
        when(mockCommentValidationService.validateCommentIds(any(), any())).then(invocation -> {
            Stream<Integer> commentIds = invocation.getArgument(1);
            Collection<String> problems = invocation.getArgument(0);
            problems.add("Received comment IDs: " + commentIds.collect(Collectors.toList()));
            return commentList;
        });
        List<SampleResult> srs = Stream.of(10, 11, null, 12, 13)
                .map(i -> {
                    SampleResult sr = new SampleResult();
                    sr.setCommentId(i);
                    return sr;
                }).collect(Collectors.toList());
        List<LabwareResult> lrs = List.of(
                new LabwareResult("STAN-01", List.of()),
                new LabwareResult("STAN-02", srs.subList(0, 3)),
                new LabwareResult("STAN-03", srs.subList(3, 5))
        );

        final List<String> problems = new ArrayList<>();

        assertThat(service.validateComments(problems, lrs)).containsExactlyInAnyOrderEntriesOf(Map.of(17, commentList.get(0), 18, commentList.get(1)));
        verify(mockCommentValidationService).validateCommentIds(eq(problems), any());
        assertThat(problems).containsExactly("Received comment IDs: [10, 11, 12, 13]");
    }

    @ParameterizedTest
    @CsvSource(value={"true,false", "true,true", "false,true", "false,false"})
    public void testLookUpPrecedingOps(boolean anyStained, boolean anyUnstained) {
        OperationType opType = EntityFactory.makeOperationType("Setup op", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.STAIN);
        final List<String> problems = new ArrayList<>();
        int numLabware = (anyStained ? 1 : 0) + (anyUnstained ? 1 : 0);
        List<Labware> labware;
        Labware slw = null;
        Set<Integer> labwareIds;
        if (numLabware==0) {
            labware = List.of();
            labwareIds = Set.of();
        } else {
            LabwareType lt = EntityFactory.getTubeType();
            labware = new ArrayList<>(numLabware);
            labwareIds = new HashSet<>(numLabware);
            if (anyStained) {
                slw = EntityFactory.makeEmptyLabware(lt);
                slw.setBarcode("STAN-S");
                labware.add(slw);
                labwareIds.add(slw.getId());
            }
            if (anyUnstained) {
                Labware ulw = EntityFactory.makeEmptyLabware(lt);
                ulw.setBarcode("STAN-U");
                labware.add(ulw);
                labwareIds.add(ulw.getId());
            }
        }

        if (numLabware==0) {
            assertThat(service.lookUpPrecedingOps(problems, opType, labware)).isEmpty();
            assertThat(problems).isEmpty();
            verifyNoInteractions(mockOpRepo);
            verify(service, never()).makeLabwareOpIdMap(any());
            return;
        }

        Operation op = new Operation();
        op.setId(17);
        op.setOperationType(opType);
        List<Operation> ops = (anyStained ? List.of(op) : List.of());
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(ops);
        Map<Integer, Integer> opsMap = (slw!=null ? Map.of(slw.getId(), op.getId()) : Map.of());
        doReturn(opsMap).when(service).makeLabwareOpIdMap(any());

        assertSame(opsMap, service.lookUpPrecedingOps(problems, opType, labware));

        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(opType, labwareIds);
        verify(service).makeLabwareOpIdMap(ops);

        if (anyUnstained) {
            assertThat(problems).containsExactly("No Setup op operation has been recorded on the following labware: [STAN-U]");
        } else {
            assertThat(problems).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource(value={
            ",,true",
            "-1,-1,true",
            "-1,1,true",
            "1,-1,false",
            "1,1,false",
            "0,-1,true",
            "0,1,false",
    })
    public void testSupersedes(Integer timeDiff, Integer idDiff, boolean expected) {
        Operation a = new Operation();
        a.setId(100);
        a.setPerformed(LocalDateTime.of(2021,9,9,12,0));
        Operation b;
        if (timeDiff==null) {
            b = null;
        } else {
            b = new Operation();
            b.setPerformed(a.getPerformed().plusHours(timeDiff));
            b.setId(a.getId() + idDiff);
        }

        assertEquals(expected, service.supersedes(a,b));
    }

    @Test
    public void testMakeLabwareOpIdMap() {
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);
        OperationType opType = new OperationType(1, "Stain");
        final List<Labware> bothLw = List.of(labware[0], labware[1]);
        final List<Labware> lw0only = List.of(labware[0]);
        final List<Labware> lw1only = List.of(labware[1]);

        Operation op1 = EntityFactory.makeOpForLabware(opType, bothLw, bothLw);
        op1.setPerformed(LocalDateTime.of(2021,9,9,12,0));
        Operation op2 = EntityFactory.makeOpForLabware(opType, lw1only, lw1only);
        op2.setPerformed(op1.getPerformed().plusHours(1));
        Operation op3 = EntityFactory.makeOpForLabware(opType, lw0only, lw0only);
        op3.setPerformed(op1.getPerformed().minusHours(1));

        Map<Integer, Integer> map = service.makeLabwareOpIdMap(List.of(op1, op2, op3));

        assertThat(map).containsExactlyInAnyOrderEntriesOf(Map.of(labware[0].getId(), op1.getId(), labware[1].getId(), op2.getId()));
    }

    @Test
    public void testCreateResults() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, 10, sam1.getTissue(), sam1.getBioState());
        int sam1id = sam1.getId();
        int sam2id = sam2.getId();

        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw1 = EntityFactory.makeLabware(lt, sam1, sam2);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        lw2.getFirstSlot().setSamples(List.of(sam1, sam2));

        Address A1 = new Address(1,1);
        Address A2 = new Address(1,2);
        List<SampleResult> srs1 = List.of(
                new SampleResult(A1, PassFail.pass, null),
                new SampleResult(A2, PassFail.fail, 17)
        );

        LabwareResult lr1 = new LabwareResult(lw1.getBarcode(), srs1);

        List<SampleResult> srs2 = List.of(
                new SampleResult(A1, PassFail.fail, 18)
        );

        LabwareResult lr2 = new LabwareResult(lw2.getBarcode(), srs2);

        Comment com1 = new Comment(17, "com1", "cats");
        Comment com2 = new Comment(18, "com2", "cats");

        User user = EntityFactory.getUser();
        OperationType resultOpType = new OperationType(2, "Record result");
        Work work = new Work(50, "SGP500", null, null, null, Work.Status.active);
        int stainId1 = 70, stainId2 = 71;
        Map<Integer, Integer> stainIdMap = Map.of(lw1.getId(), stainId1, lw2.getId(), stainId2);

        Operation op1 = new Operation(500, resultOpType, null, null, null);
        Operation op2 = new Operation(501, resultOpType, null, null, null);
        when(mockOpService.createOperationInPlace(resultOpType, user, lw1, null, null))
                .thenReturn(op1);
        when(mockOpService.createOperationInPlace(resultOpType, user, lw2, null, null))
                .thenReturn(op2);

        OperationResult opResult = service.createResults(user, resultOpType, List.of(lr1, lr2),
                UCMap.from(Labware::getBarcode, lw1, lw2),
                stainIdMap, Map.of(17, com1, 18, com2), work);

        verify(mockOpService).createOperationInPlace(resultOpType, user, lw1, null, null);
        verify(mockOpService).createOperationInPlace(resultOpType, user, lw2, null, null);

        verify(mockOpCommentRepo).saveAll(List.of(
                new OperationComment(null, com1, op1.getId(), sam2id, lw1.getSlot(A2).getId(), null),
                new OperationComment(null, com2, op2.getId(), sam1id, lw2.getSlot(A1).getId(), null),
                new OperationComment(null, com2, op2.getId(), sam2id, lw2.getSlot(A1).getId(), null)
        ));
        verify(mockResOpRepo).saveAll(List.of(
                new ResultOp(null, PassFail.pass, op1.getId(), sam1id, lw1.getSlot(A1).getId(), stainId1),
                new ResultOp(null, PassFail.fail, op1.getId(), sam2id, lw1.getSlot(A2).getId(), stainId1),
                new ResultOp(null, PassFail.fail, op2.getId(), sam1.getId(), lw2.getSlot(A1).getId(), stainId2),
                new ResultOp(null, PassFail.fail, op2.getId(), sam2.getId(), lw2.getSlot(A1).getId(), stainId2)
        ));
        verify(mockWorkService).link(work, List.of(op1, op2));

        assertThat(opResult.getOperations()).containsExactlyInAnyOrder(op1, op2);
        assertThat(opResult.getLabware()).containsExactlyInAnyOrder(lw1, lw2);
    }
}
