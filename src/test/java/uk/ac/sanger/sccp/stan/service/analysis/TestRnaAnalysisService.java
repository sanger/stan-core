package uk.ac.sanger.sccp.stan.service.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.RNAAnalysisRequest.RNAAnalysisLabware;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.analysis.AnalysisMeasurementValidator.AnalysisType;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.service.analysis.RNAAnalysisServiceImp.DV200_OP_NAME;
import static uk.ac.sanger.sccp.stan.service.analysis.RNAAnalysisServiceImp.RIN_OP_NAME;

/**
 * Tests {@link RNAAnalysisServiceImp}
 * @author dr6
 */
public class TestRnaAnalysisService {
    private LabwareValidatorFactory mockLabwareValidatorFactory;
    private AnalysisMeasurementValidatorFactory mockMeasurementValidatorFactory;
    private WorkService mockWorkService;
    private OperationService mockOpService;
    private CommentValidationService mockCommentValidationService;
    private MeasurementRepo mockMeasurementRepo;
    private OperationCommentRepo mockOpComRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private LabwareRepo mockLwRepo;

    private RNAAnalysisServiceImp service;

    @BeforeEach
    void setup() {
        mockLabwareValidatorFactory = mock(LabwareValidatorFactory.class);
        mockMeasurementValidatorFactory = mock(AnalysisMeasurementValidatorFactory.class);
        mockWorkService = mock(WorkService.class);
        mockOpService = mock(OperationService.class);
        mockCommentValidationService = mock(CommentValidationService.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);
        mockOpComRepo = mock(OperationCommentRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        OperationRepo mockOpRepo = mock(OperationRepo.class);
        mockLwRepo = mock(LabwareRepo.class);

        service = spy(new RNAAnalysisServiceImp(mockLabwareValidatorFactory, mockMeasurementValidatorFactory,
                mockWorkService, mockOpService, mockCommentValidationService, mockOpTypeRepo, mockOpRepo,
                mockLwRepo, mockMeasurementRepo, mockOpComRepo));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testPerform(boolean valid) {
        User user = EntityFactory.getUser();
        RNAAnalysisRequest request = new RNAAnalysisRequest("RIN", List.of(
                new RNAAnalysisLabware("STAN-A1", "SGP11", 4, List.of())
        ));
        var requestLabware = request.getLabware();
        OperationType opType = EntityFactory.makeOperationType("RIN", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.ANALYSIS);
        Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        UCMap<List<StringMeasurement>> measMap = new UCMap<>(1);
        measMap.put(lw.getBarcode(), List.of(new StringMeasurement("RIN", "50")));
        Map<Integer, Comment> commentMap = Map.of(15, new Comment(15, "Wrong colour", "analysis"));
        Work work = new Work(60, "SGP60", null, null, null, Work.Status.active);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work);

        final String problemMessage = "Bad operation type";
        if (valid) {
            doReturn(opType).when(service).validateOpType(any(), any());
        } else {
            doAnswer(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add(problemMessage);
                return opType;
            }).when(service).validateOpType(any(), any());
        }

        doReturn(lwMap).when(service).validateLabware(any(), any());
        doReturn(measMap).when(service).validateMeasurements(any(), any(), any());
        doReturn(commentMap).when(service).validateComments(any(), any());
        doReturn(workMap).when(service).validateWork(any(), any());

        if (valid) {
            OperationResult opRes = new OperationResult(List.of(new Operation()), List.of(lw));
            doReturn(opRes).when(service).recordAnalysis(any(), any(), any(), any(), any(), any(), any());
            assertSame(opRes, service.perform(user, request));

            verify(service).recordAnalysis(user, request, opType, lwMap, measMap, commentMap, workMap);
        } else {
            var ex = assertThrows(ValidationException.class, () -> service.perform(user, request));
            assertThat(ex).hasMessage("The request could not be validated.");
            //noinspection unchecked
            assertThat((Collection<Object>) ex.getProblems()).contains(problemMessage);

            verify(service, never()).recordAnalysis(any(), any(), any(), any(), any(), any(), any());
        }

        //noinspection unchecked
        ArgumentCaptor<Collection<String>> problemsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(service).validateOpType(problemsCaptor.capture(), eq("RIN"));
        Collection<String> problems = problemsCaptor.getValue();

        verify(service).validateLabware(same(problems), same(requestLabware));
        verify(service).validateMeasurements(same(problems), same(opType), same(requestLabware));
        verify(service).validateComments(same(problems), same(requestLabware));
        verify(service).validateWork(same(problems), same(requestLabware));
    }

    @ParameterizedTest
    @MethodSource("validateOpTypeArgs")
    public void testValidateOpType(String name, OperationType opType, String expectedProblem) {
        when(mockOpTypeRepo.findByName(name)).thenReturn(Optional.ofNullable(opType));
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(opType, service.validateOpType(problems, name));
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    static Stream<Arguments> validateOpTypeArgs() {
        return Arrays.stream(new Object[][] {
                {null, null, "No operation type specified."},
                {"", null, "No operation type specified."},
                {"Bananas", null, "Unknown operation type: \"Bananas\""},
                {"Section", EntityFactory.makeOperationType("Section", null), "Not an analysis operation: Section"},
                {"RIN", EntityFactory.makeOperationType("RIN", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.ANALYSIS), null},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("validateLabwareArgs")
    public void testValidateLabware(List<String> barcodes, List<Labware> labware, List<String> validatorProblems, List<String> expectedProblems) {
        List<RNAAnalysisLabware> reqLws = barcodes.stream()
                .map(bc -> new RNAAnalysisLabware(bc, null, null, null))
                .collect(toList());
        final List<String> problems = new ArrayList<>(expectedProblems.size());
        if (reqLws.isEmpty()) {
            assertThat(service.validateLabware(problems, reqLws)).isEmpty();
            assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
            return;
        }
        LabwareValidator lv = mock(LabwareValidator.class);
        when(mockLabwareValidatorFactory.getValidator()).thenReturn(lv);
        when(lv.loadLabware(any(), any())).thenReturn(labware);
        when(lv.getLabware()).thenReturn(labware);
        when(lv.getErrors()).thenReturn(validatorProblems);
        assertThat(service.validateLabware(problems, reqLws).values()).containsExactlyInAnyOrderElementsOf(labware);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        verify(lv).loadLabware(mockLwRepo, barcodes);
    }

    static Stream<Arguments> validateLabwareArgs() {
        Labware lw = EntityFactory.getTube();

        return Arrays.stream(new Object[][] {
                {List.of(), List.of(), List.of(), List.of("No labware specified.")},
                {List.of(lw.getBarcode()), List.of(lw), List.of(), List.of()},
                {List.of(lw.getBarcode()), List.of(lw), List.of("Labware is bad."), List.of("Labware is bad.")},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("validateMeasurementsArgs")
    public void testValidateMeasurements(OperationType opType, List<RNAAnalysisLabware> reqLabware,
                                         AnalysisType analysisType,
                                         Set<String> validatorProblems, List<String> expectedProblems) {
        List<String> problems = new ArrayList<>(expectedProblems.size());
        if (analysisType==null) {
            assertThat(service.validateMeasurements(problems, opType, reqLabware)).isEmpty();
            verifyNoInteractions(mockMeasurementValidatorFactory);
            assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
            return;
        }
        MeasurementValidator mockValidator = mock(MeasurementValidator.class);
        when(mockMeasurementValidatorFactory.makeValidator(analysisType)).thenReturn(mockValidator);
        when(mockValidator.validateMeasurements(any())).thenAnswer(invocation -> {
            List<StringMeasurement> sms = invocation.getArgument(0);
            return sms.stream()
                    .map(sm -> new StringMeasurement(sm.getName()+"X", sm.getValue()+"Y"))
                    .collect(toList());
        });
        when(mockValidator.compileProblems()).thenReturn(validatorProblems);
        UCMap<List<StringMeasurement>> expected = new UCMap<>(reqLabware.size());
        for (var rlw : reqLabware) {
            if (rlw.getMeasurements()!=null && !rlw.getMeasurements().isEmpty()) {
                List<StringMeasurement> srlw = rlw.getMeasurements().stream()
                        .map(sm -> new StringMeasurement(sm.getName()+"X", sm.getValue()+"Y"))
                        .collect(toList());
                expected.put(rlw.getBarcode(), srlw);
            }
        }

        assertEquals(expected, service.validateMeasurements(problems, opType, reqLabware));
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateMeasurementsArgs() {
        OperationType rin = EntityFactory.makeOperationType(RIN_OP_NAME, null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.ANALYSIS);
        OperationType dv200 = EntityFactory.makeOperationType(RNAAnalysisServiceImp.DV200_OP_NAME, null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.ANALYSIS);
        OperationType baste = EntityFactory.makeOperationType("Baste", null);

        List<RNAAnalysisLabware> rlws = List.of(
                new RNAAnalysisLabware("STAN-1", null, null, List.of(new StringMeasurement("RIN", "200"))),
                new RNAAnalysisLabware("STAN-2", null, null, List.of()),
                new RNAAnalysisLabware("STAN-3", null, null, List.of(new StringMeasurement("Bananas", "12")))
        );

        return Arrays.stream(new Object[][] {
                {null, rlws, null, null, List.of()},
                {rin, List.of(), null, null, List.of()},
                {baste, List.of(), null, null, List.of()},
                {baste, rlws, null, null, List.of("Unexpected measurements for operation type Baste.")},
                {rin, rlws, AnalysisType.RIN, Set.of(), List.of()},
                {dv200, rlws, AnalysisType.DV200, Set.of(), List.of()},
                {rin, rlws, AnalysisType.RIN, Set.of("Invalid things."), List.of("Invalid things.")},
        }).map(Arguments::of);
    }

    @Test
    public void testValidateComments() {
        final String problem = "Some problem.";
        final Comment comment = new Comment(1, "Exploding", "analysis");
        final List<Integer> requestedCommentIds = new ArrayList<>(2);
        when(mockCommentValidationService.validateCommentIds(any(), any())).then(invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            problems.add(problem);
            Stream<Integer> commentIdStream = invocation.getArgument(1);
            commentIdStream.forEach(requestedCommentIds::add);
            return List.of(comment);
        });
        List<String> problems = new ArrayList<>(1);
        List<RNAAnalysisLabware> rlws = List.of(
                new RNAAnalysisLabware("STAN-1", null, 2, null),
                new RNAAnalysisLabware("STAN-2", null, null, null),
                new RNAAnalysisLabware("STAN-3", null, 3, null)
        );

        assertEquals(Map.of(1, comment), service.validateComments(problems, rlws));
        assertThat(problems).containsExactly(problem);
        assertEquals(List.of(2,3), requestedCommentIds);
    }

    @Test
    public void testValidateWork() {
        final String problem = "Some problem.";
        Work work = new Work(15, "SGP15", null, null, null, Work.Status.active);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work);
        when(mockWorkService.validateUsableWorks(any(), any())).then(invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            problems.add(problem);
            return workMap;
        });
        List<RNAAnalysisLabware> rlws = List.of(
                new RNAAnalysisLabware("STAN-1", "SGP15", null, null),
                new RNAAnalysisLabware("STAN-2", "SGP15", null, null),
                new RNAAnalysisLabware("STAN-3", "SGP16", null, null),
                new RNAAnalysisLabware("STAN-4", "SGP16", null, null)
        );
        final List<String> problems = new ArrayList<>(1);
        assertSame(workMap, service.validateWork(problems, rlws));
        assertThat(problems).containsExactly(problem);
        verify(mockWorkService).validateUsableWorks(problems, Set.of("SGP15", "SGP16"));
    }

    @Test
    public void testRecordAnalysisSimple() {
        Labware lw = EntityFactory.getTube();
        Work work1 = new Work(50, "SGP50", null, null, null, Work.Status.active);
        RNAAnalysisRequest request = new RNAAnalysisRequest(RIN_OP_NAME,
                List.of(new RNAAnalysisLabware(lw.getBarcode(), work1.getWorkNumber(), null, null))
        );
        OperationType opType = new OperationType(4, RIN_OP_NAME);
        Operation op = new Operation(17, opType, null, null, null);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        User user = EntityFactory.getUser();
        UCMap<List<StringMeasurement>> smMap = new UCMap<>(0);
        Map<Integer, Comment> commentMap = Map.of();
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work1);

        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);
        when(mockWorkService.validateUsableWorks(any(), any())).thenReturn(workMap);

        OperationResult result = service.recordAnalysis(user, request, opType, lwMap, smMap, commentMap, workMap);
        assertThat(result.getOperations()).containsExactly(op);
        assertThat(result.getLabware()).containsExactly(lw);

        verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        verify(mockWorkService).link(work1,List.of(op));

        verify(service, never()).addMeasurements(any(), any(), any(), any());
        verify(service, never()).addOpComs(any(), any(), any(), any());
        verifyNoInteractions(mockMeasurementRepo);
        verifyNoInteractions(mockOpComRepo);
    }

    @Test
    public void testRecordAnalysisDetailed() {
        User user = EntityFactory.getUser();
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), EntityFactory.getSample());
        Labware lw3 = EntityFactory.makeLabware(lw1.getLabwareType(), EntityFactory.getSample());
        Labware lw4 = EntityFactory.makeLabware(lw1.getLabwareType(), EntityFactory.getSample());
        OperationType opType = new OperationType(5, DV200_OP_NAME);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2, lw3, lw4);
        Work work1 = new Work(1, "SGP1", null, null, null, Work.Status.active);
        Work work2 = new Work(2, "SGP2", null, null, null, Work.Status.active);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work1, work2);
        UCMap<List<StringMeasurement>> smMap = new UCMap<>(2);
        final StringMeasurement sm1 = new StringMeasurement("DV200 lower", "20.0");
        final StringMeasurement sm2 = new StringMeasurement("DV200 upper", "30.0");
        final StringMeasurement sm3 = new StringMeasurement("DV200", "40.0");
        smMap.put(lw1.getBarcode(), List.of(sm1, sm2));
        smMap.put(lw2.getBarcode(), List.of(sm3));
        Comment com1 = new Comment(5, "Custard", "curtains");
        Comment com2 = new Comment(6, "Rhubarb", "curtains");
        Map<Integer, Comment> commentMap = Map.of(com1.getId(), com1, com2.getId(), com2);

        List<Operation> ops = IntStream.range(0, lwMap.size())
                .mapToObj(i -> new Operation(100+i, opType, null, null, null))
                .collect(toList());
        Integer[] opIds = ops.stream().map(Operation::getId).toArray(Integer[]::new);

        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any()))
                .thenReturn(ops.get(0), ops.get(1), ops.get(2), ops.get(3));

        RNAAnalysisRequest request = new RNAAnalysisRequest(DV200_OP_NAME, List.of(
                new RNAAnalysisLabware(lw1.getBarcode(), work1.getWorkNumber(), com1.getId(), null),
                new RNAAnalysisLabware(lw2.getBarcode(), work2.getWorkNumber(), null, null),
                new RNAAnalysisLabware(lw3.getBarcode(), null, com2.getId(), null),
                new RNAAnalysisLabware(lw4.getBarcode(), work1.getWorkNumber(), null, null)
        ));

        OperationResult result = service.recordAnalysis(user, request, opType, lwMap, smMap, commentMap, workMap);

        assertThat(result.getOperations()).containsExactlyInAnyOrderElementsOf(ops);
        assertThat(result.getLabware()).containsExactlyInAnyOrder(lw1, lw2, lw3, lw4);

        verify(mockOpService).createOperationInPlace(opType, user, lw1, null, null);
        verify(mockOpService).createOperationInPlace(opType, user, lw2, null, null);
        verify(mockOpService).createOperationInPlace(opType, user, lw3, null, null);
        verify(mockOpService).createOperationInPlace(opType, user, lw4, null, null);

        verify(service).addMeasurements(any(), eq(opIds[0]), same(lw1), same(smMap.get(lw1.getBarcode())));
        verify(service).addMeasurements(any(), eq(opIds[1]), same(lw2), same(smMap.get(lw2.getBarcode())));
        verify(service).addOpComs(any(), same(com1), eq(opIds[0]), same(lw1));
        verify(service).addOpComs(any(), same(com2), eq(opIds[2]), same(lw3));

        verify(mockWorkService).link(work1, List.of(ops.get(0), ops.get(3)));
        verify(mockWorkService).link(work2, List.of(ops.get(1)));

        Integer sampleId = lw1.getFirstSlot().getSamples().get(0).getId();
        Integer[] slotIds = Stream.of(lw1, lw2, lw3, lw4).map(lw -> lw.getFirstSlot().getId()).toArray(Integer[]::new);
        verify(mockMeasurementRepo).saveAll(List.of(
                new Measurement(null, "DV200 lower", "20.0", sampleId, opIds[0], slotIds[0]),
                new Measurement(null, "DV200 upper", "30.0", sampleId, opIds[0], slotIds[0]),
                new Measurement(null, "DV200", "40.0", sampleId, opIds[1], slotIds[1])
        ));

        verify(mockOpComRepo).saveAll(List.of(
                new OperationComment(null, com1, opIds[0], sampleId, slotIds[0], null),
                new OperationComment(null, com2, opIds[2], sampleId, slotIds[2], null)
        ));
    }

    @Test
    public void testAddMeasurements() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId() + 1, 6, sam1.getTissue(), sam1.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(1, 3);
        Labware lw = EntityFactory.makeLabware(lt, sam1, sam2);
        lw.getFirstSlot().getSamples().add(sam2);
        List<StringMeasurement> sms = List.of(
                new StringMeasurement("DV200 lower", "10.0"),
                new StringMeasurement("DV200 upper", "20.0")
        );
        Integer opId = 700;

        final List<Measurement> measurements = new ArrayList<>(6);
        service.addMeasurements(measurements, opId, lw, sms);

        Integer sam1id = sam1.getId();
        Integer sam2id = sam2.getId();
        Integer slot1id = lw.getFirstSlot().getId();
        Integer slot2id = lw.getSlots().get(1).getId();

        assertThat(measurements).containsExactlyInAnyOrder(
                new Measurement(null, "DV200 lower", "10.0", sam1id, opId, slot1id),
                new Measurement(null, "DV200 lower", "10.0", sam2id, opId, slot1id),
                new Measurement(null, "DV200 lower", "10.0", sam2id, opId, slot2id),
                new Measurement(null, "DV200 upper", "20.0", sam1id, opId, slot1id),
                new Measurement(null, "DV200 upper", "20.0", sam2id, opId, slot1id),
                new Measurement(null, "DV200 upper", "20.0", sam2id, opId, slot2id)
        );
    }

    @Test
    public void testAddOpComs() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId() + 1, 6, sam1.getTissue(), sam1.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(1, 3);
        Labware lw = EntityFactory.makeLabware(lt, sam1, sam2);
        lw.getFirstSlot().getSamples().add(sam2);
        Comment com = new Comment(17, "Ryuk", "shinigami");
        Integer opId = 700;

        List<OperationComment> opComs = new ArrayList<>(3);
        service.addOpComs(opComs, com, opId, lw);

        Integer sam1id = sam1.getId();
        Integer sam2id = sam2.getId();
        Integer slot1id = lw.getFirstSlot().getId();
        Integer slot2id = lw.getSlots().get(1).getId();

        assertThat(opComs).containsExactlyInAnyOrder(
                new OperationComment(null, com, opId, sam1id, slot1id, null),
                new OperationComment(null, com, opId, sam2id, slot1id, null),
                new OperationComment(null, com, opId, sam2id, slot2id, null)
        );
    }
}
