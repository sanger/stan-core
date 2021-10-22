package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ExtractResultRequest;
import uk.ac.sanger.sccp.stan.request.ExtractResultRequest.ExtractResultLabware;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ExtractResultServiceImp}
 * @author dr6
 */
public class TestExtractResultService {
    private LabwareValidatorFactory mockLabwareValidatorFactory;
    private Sanitiser<String> mockConcentrationSanitiser;
    private WorkService mockWorkService;
    private CommentValidationService mockCommentValidationService;
    private OperationService mockOpService;
    private LabwareRepo mockLwRepo;
    private OperationCommentRepo mockOpCommentRepo;
    private MeasurementRepo mockMeasurementRepo;
    private ResultOpRepo mockResultOpRepo;

    private ExtractResultServiceImp service;

    @BeforeEach
    void setup() {
        mockLabwareValidatorFactory = mock(LabwareValidatorFactory.class);
        //noinspection unchecked
        mockConcentrationSanitiser = mock(Sanitiser.class);
        mockWorkService = mock(WorkService.class);
        mockCommentValidationService = mock(CommentValidationService.class);
        mockOpService = mock(OperationService.class);
        mockLwRepo = mock(LabwareRepo.class);
        OperationTypeRepo mockOpTypeRepo = mock(OperationTypeRepo.class);
        OperationRepo mockOpRepo = mock(OperationRepo.class);
        mockOpCommentRepo = mock(OperationCommentRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);
        mockResultOpRepo = mock(ResultOpRepo.class);

        service = spy(new ExtractResultServiceImp(mockLabwareValidatorFactory, mockConcentrationSanitiser,
                mockWorkService, mockCommentValidationService, mockOpService, mockLwRepo, mockOpTypeRepo,
                mockOpRepo, mockOpCommentRepo, mockMeasurementRepo, mockResultOpRepo));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testRecordExtractResult(boolean succeeds) {
        OperationType opType = new OperationType(1, "Record result");
        doReturn(opType).when(service).loadOpType(anyCollection(), any());
        Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        doReturn(lwMap).when(service).loadLabware(anyCollection(), any());
        doNothing().when(service).validateResults(anyCollection(), any());
        Map<Integer, Comment> commentMap = Map.of(7, new Comment(7, "Bananas", "fruit"));
        doReturn(commentMap).when(service).validateComments(anyCollection(), any());
        doNothing().when(service).validateMeasurements(anyCollection(), any());
        final String workNumber = "SGP50";
        Work work = new Work(4, workNumber, null, null, null, Work.Status.active);
        doReturn(work).when(mockWorkService).validateUsableWork(anyCollection(), any());
        Map<Integer, Integer> extractMap = Map.of(44,55);
        doReturn(extractMap).when(service).lookUpExtracts(anyCollection(), any());

        List<ExtractResultLabware> erls = List.of(
                new ExtractResultLabware(lw.getBarcode(), PassFail.pass, 7)
        );
        ExtractResultRequest request = new ExtractResultRequest(erls, workNumber);

        if (succeeds) {
            User user = EntityFactory.getUser();
            OperationResult opRes = new OperationResult(List.of(), List.of(lw));
            doReturn(opRes).when(service).createResults(any(), any(), any(), any(), any(), any(), any());

            assertSame(opRes, service.recordExtractResult(user, request));
            verify(service).createResults(user, opType, erls, lwMap, extractMap, commentMap, work);
        } else {
            ValidationException ex = assertThrows(ValidationException.class, () ->
                    service.recordExtractResult(null, request));
            assertThat(ex).hasMessage("The request could not be validated.");
            //noinspection unchecked
            assertThat((Collection<Object>) ex.getProblems()).containsExactly("No user specified.");
            verify(service, never()).createResults(any(), any(), any(), any(), any(), any(), any());
        }

        //noinspection unchecked
        ArgumentCaptor<Collection<String>> problemsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(service).loadOpType(problemsCaptor.capture(), eq("Record result"));
        Collection<String> problems = problemsCaptor.getValue();
        assertNotNull(problems);
        verify(service).validateLabware(same(problems), eq(erls));
        verify(service).validateResults(same(problems), eq(erls));
        verify(service).validateComments(same(problems), eq(erls));
        verify(service).validateMeasurements(same(problems), eq(erls));
        verify(service).lookUpExtracts(same(problems), eq(lwMap.values()));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testValidateLabware(boolean any) {
        List<String> problems = new ArrayList<>(1);
        if (!any) {
            assertThat(service.validateLabware(problems, List.of())).isEmpty();
            assertThat(problems).containsExactly("No labware specified.");
            verifyNoInteractions(mockLabwareValidatorFactory);
            return;
        }
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLabwareValidatorFactory.getValidator()).thenReturn(val);
        String lwError = "Bears broke it.";
        when(val.getErrors()).thenReturn(List.of(lwError));
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        when(val.getLabware()).thenReturn(List.of(lw1, lw2));
        List<ExtractResultLabware> erls = List.of(
                new ExtractResultLabware(lw1.getBarcode(), null, null, null),
                new ExtractResultLabware(lw2.getBarcode(), null, null, null)
        );
        UCMap<Labware> lwMap = service.validateLabware(problems, erls);
        assertThat(lwMap.values()).containsExactlyInAnyOrder(lw1, lw2);
        assertThat(problems).containsExactly(lwError);
        verify(val).loadLabware(mockLwRepo, List.of(lw1.getBarcode(), lw2.getBarcode()));
    }

    @Test
    public void testValidateResults() {
        List<ExtractResultLabware> erls = List.of(
                new ExtractResultLabware("STAN-1", PassFail.pass, "10.1"),
                new ExtractResultLabware("STAN-2", PassFail.fail, 2),
                new ExtractResultLabware("STAN-3", null, null, null),
                new ExtractResultLabware("STAN-4", PassFail.pass, "40.1", 4),
                new ExtractResultLabware("STAN-5", PassFail.fail, "50.1", 5),
                new ExtractResultLabware("STAN-6", PassFail.pass, null, null),
                new ExtractResultLabware("STAN-7", PassFail.fail, null, null)
        );
        List<String> problems = new ArrayList<>(5);
        service.validateResults(problems, erls);

        assertThat(problems).containsExactlyInAnyOrder(
                "No result specified for labware STAN-3.",
                "Unexpected comment specified for pass on labware STAN-4.",
                "No concentration specified for pass on labware STAN-6.",
                "No comment specified for fail on labware STAN-7.",
                "Unexpected concentration specified for fail on labware STAN-5."
        );
    }

    @Test
    public void testValidateComments() {
        List<ExtractResultLabware> erls = List.of(
                new ExtractResultLabware("STAN-1", PassFail.pass, "11.5"),
                new ExtractResultLabware("STAN-1", PassFail.fail, 14),
                new ExtractResultLabware("STAN-1", PassFail.fail, 15)
        );
        final Comment com1 = new Comment(14, "Custard", "vegetables");
        final Comment com2 = new Comment(15, "Crumble", "vitamins");
        when(mockCommentValidationService.validateCommentIds(any(), any())).then(invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            Stream<Integer> ids = invocation.getArgument(1);
            problems.add("Bad comment ids: "+ids.collect(toList()));
            return List.of(com1, com2);
        });
        List<String> problems = new ArrayList<>(1);

        Map<Integer, Comment> commentMap = service.validateComments(problems, erls);

        assertThat(commentMap).containsExactlyInAnyOrderEntriesOf(Map.of(com1.getId(), com1, com2.getId(), com2));
        assertThat(problems).containsExactly("Bad comment ids: [14, 15]");
        verify(mockCommentValidationService).validateCommentIds(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidateMeasurements(boolean anyBad) {
        when(mockConcentrationSanitiser.isValid(any())).then(invocation -> {
            String string = invocation.getArgument(0);
            return (string!=null && string.indexOf('!') < 0);
        });
        List<String> problems = new ArrayList<>(anyBad ? 1 : 0);
        List<String> concs;
        if (anyBad) {
            concs = Arrays.asList("5.5", null, "!alpha", "6.5", null, "!beta", "7.6");
        } else {
            concs = Arrays.asList("5.5", null, "6.5", null, "7.6");
        }
        List<ExtractResultLabware> erls = concs.stream()
                .map(conc -> new ExtractResultLabware("STAN-X", PassFail.pass, conc))
                .collect(toList());

        service.validateMeasurements(problems, erls);

        if (anyBad) {
            assertThat(problems).containsOnly("Invalid values given for concentration: [!alpha, !beta]");
        } else {
            assertThat(problems).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"No such op type.", "Missing ops.", "No labware.", "No problem."})
    public void testLookUpExtracts(String condition) {
        boolean opTypeExists = true;
        boolean anyLabware = true;
        boolean allFine = true;
        final String opTypeError = "No such op type.";
        final String opError = "Missing ops.";
        final String noLw = "No labware.";
        final String noProblem = "No problem.";
        switch (condition) {
            case opTypeError: opTypeExists = false; break;
            case noLw: anyLabware = false; break;
            case opError: allFine = false; break;
            case noProblem: break;
            default: throw new IllegalArgumentException("Unexpected condition: "+condition);
        }
        OperationType opType;
        if (opTypeExists) {
            opType = new OperationType(5, "Extract");
            doReturn(opType).when(service).loadOpType(any(), any());
        } else {
            opType = null;
            doAnswer(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add(opTypeError);
                return null;
            }).when(service).loadOpType(any(), any());
        }

        List<Labware> lwList;
        if (anyLabware) {
            lwList = List.of(EntityFactory.getTube());
        } else {
            lwList = List.of();
        }
        Map<Integer, Integer> idMap;
        if (opTypeExists && anyLabware) {
            idMap = Map.of(4, 5);
            if (allFine) {
                doReturn(idMap).when(service).lookUpLatestOpIds(any(), any(), any());
            } else {
                doAnswer(invocation -> {
                    Collection<String> problems = invocation.getArgument(0);
                    problems.add(opError);
                    return idMap;
                }).when(service).lookUpLatestOpIds(any(), any(), any());
            }
        } else {
            idMap = Map.of();
        }

        List<String> problems = new ArrayList<>(1);

        Map<Integer, Integer> result = service.lookUpExtracts(problems, lwList);
        assertThat(result).containsExactlyInAnyOrderEntriesOf(idMap);

        verify(service).loadOpType(problems, "Extract");
        if (!opTypeExists) {
            assertThat(problems).containsExactly(opTypeError);
        } else if (allFine) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(opError);
        }

        if (opTypeExists && anyLabware) {
            verify(service).lookUpLatestOpIds(problems, opType, lwList);
        } else {
            verify(service, never()).lookUpLatestOpIds(any(), any(), any());
        }
    }

    @Test
    public void testCreateResults() {
        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), sample);
        int sampleId = sample.getId();
        int slot1id = lw1.getFirstSlot().getId();
        int slot2id = lw2.getFirstSlot().getId();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        Map<Integer, Integer> extractMap = Map.of(lw1.getId(), 400, lw2.getId(), 401);
        final Comment comment = new Comment(17, "pi", "greek");
        Map<Integer, Comment> commentMap = Map.of(comment.getId(), comment);
        Work work = new Work(5, "SGP50", null, null, null, Work.Status.active);
        OperationType resultOpType = new OperationType(5, "Record result");
        User user = EntityFactory.getUser();
        List<ExtractResultLabware> erls = List.of(
                new ExtractResultLabware(lw1.getBarcode(), PassFail.pass, "55"),
                new ExtractResultLabware(lw2.getBarcode(), PassFail.fail, 17)
        );
        Operation op1 = new Operation(21, resultOpType, null, null, null);
        Operation op2 = new Operation(22, resultOpType, null, null, null);

        when(mockConcentrationSanitiser.sanitise("55")).thenReturn("55.00");

        when(mockOpService.createOperationInPlace(resultOpType, user, lw1, null, null))
                .thenReturn(op1);
        when(mockOpService.createOperationInPlace(resultOpType, user, lw2, null, null))
                .thenReturn(op2);

        // NB we let addResultData run, because that's easier than mocking it

        OperationResult result = service.createResults(user, resultOpType, erls, lwMap, extractMap, commentMap, work);
        assertThat(result.getOperations()).containsExactly(op1, op2);
        assertThat(result.getLabware()).containsExactly(lw1, lw2);

        verify(service).addResultData(anyCollection(), anyCollection(), anyCollection(), same(erls.get(0)),
                same(commentMap), eq(op1.getId()), eq(400), eq("55.00"), eq(slot1id), eq(sampleId));
        verify(service).addResultData(anyCollection(), anyCollection(), anyCollection(), same(erls.get(1)),
                same(commentMap), eq(op2.getId()), eq(401), isNull(), eq(slot2id), eq(sampleId));

        verify(service, times(2)).addResultData(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());

        verify(mockResultOpRepo).saveAll(List.of(
                new ResultOp(null, PassFail.pass, op1.getId(), sampleId, slot1id, 400),
                new ResultOp(null, PassFail.fail, op2.getId(), sampleId, slot2id, 401)
        ));
        verify(mockOpCommentRepo).saveAll(List.of(
                new OperationComment(null, comment, op2.getId(), sampleId, slot2id, null)
        ));
        verify(mockMeasurementRepo).saveAll(List.of(
                new Measurement(null, "Concentration", "55.00", sampleId, op1.getId(), slot1id)
        ));
        verify(mockWorkService).link(work, List.of(op1, op2));
    }

    @ParameterizedTest
    @CsvSource(value={
            "pass,,",
            "pass,55.00,",
            "fail,,4",
    })
    public void testAddResultData(PassFail pf, String concentrationValue, Integer commentId) {
        List<ResultOp> ros = new ArrayList<>(1);
        List<OperationComment> opComs = new ArrayList<>(commentId==null ? 0 : 1);
        List<Measurement> measurements = new ArrayList<>(concentrationValue==null ? 0 : 1);

        ExtractResultLabware erl = new ExtractResultLabware("STAN-1", pf, concentrationValue==null ? null : "splf", commentId);
        Comment comment = (commentId==null ? null : new Comment(commentId, "Alpha", "Alabama"));
        Map<Integer, Comment> commentMap = (commentId==null ? Map.of() : Map.of(commentId, comment));

        Integer resultOpId = 40;
        Integer refersToOpId = 30;
        Integer slotId = 500;
        Integer sampleId = 100;

        service.addResultData(ros, opComs, measurements, erl, commentMap, resultOpId, refersToOpId, concentrationValue,
                slotId, sampleId);

        assertThat(ros).containsExactly(new ResultOp(null, pf, resultOpId, sampleId, slotId, refersToOpId));
        if (concentrationValue!=null) {
            assertThat(measurements).containsExactly(new Measurement(null, "Concentration", concentrationValue,
                    sampleId, resultOpId, slotId));
        } else {
            assertThat(measurements).isEmpty();
        }
        if (commentId!=null) {
            assertThat(opComs).containsExactly(new OperationComment(null, comment, resultOpId, sampleId, slotId, null));
        } else {
            assertThat(opComs).isEmpty();
        }
    }
}
