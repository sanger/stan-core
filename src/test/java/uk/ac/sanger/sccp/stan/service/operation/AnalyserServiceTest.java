package uk.ac.sanger.sccp.stan.service.operation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AnalyserRequest;
import uk.ac.sanger.sccp.stan.request.AnalyserRequest.AnalyserLabware;
import uk.ac.sanger.sccp.stan.request.AnalyserRequest.SampleROI;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.stan.service.operation.AnalyserServiceImp.EQUIPMENT_CATEGORY;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Test {@link AnalyserServiceImp}
 */
public class AnalyserServiceTest {

    @Mock
    private LabwareValidatorFactory mockLwValFactory;
    @Mock
    private OpSearcher mockOpSearcher;
    @Mock
    private OperationService mockOpService;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private OperationRepo mockOpRepo;
    @Mock
    private Clock mockClock;
    @Mock
    private LabwareNoteRepo mockLwNoteRepo;
    @Mock
    private RoiRepo mockRoiRepo;
    @Mock(name="decodingReagentLotValidator")
    private Validator<String> mockDecodingReagentLotValidator;
    @Mock(name="runNameValidator")
    private Validator<String> mockRunNameValidator;
    @Mock(name="roiValidator")
    private Validator<String> mockRoiValidator;
    @Mock
    private ValidationHelperFactory mockValFactory;
    @Mock
    private ValidationHelper mockVal;

    private AnalyserServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    public void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(new AnalyserServiceImp(mockLwValFactory, mockOpSearcher, mockOpService, mockWorkService,
                mockLwRepo, mockOpTypeRepo, mockOpRepo, mockClock, mockLwNoteRepo, mockRoiRepo,
                mockDecodingReagentLotValidator, mockRunNameValidator, mockRoiValidator, mockValFactory));
    }

    @AfterEach
    public void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    public void testPerform() {
        User user = EntityFactory.getUser();
        final Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        Map<Integer, Operation> priorOps = Map.of(5, new Operation());
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        Equipment equipment = new Equipment(1, "Xenium 1", EQUIPMENT_CATEGORY, true);
        OperationResult opres = new OperationResult(List.of(new Operation()), List.of(lw));

        doReturn(lwMap).when(service).checkLabware(any(), any());
        doReturn(opType).when(service).checkOpType(any(), any());
        doReturn(priorOps).when(service).loadPriorOps(any(), any(), any());
        doNothing().when(service).checkTimestamp(any(), any(), any(), any());
        doReturn(workMap).when(service).loadWork(any(), any());
        doNothing().when(service).checkCassettePositions(any(), any());
        doNothing().when(service).checkRois(any(), any());
        doNothing().when(service).checkSamples(any(), any(), any());
        doNothing().when(service).validateLot(any(), any());
        doNothing().when(service).validateRunName(any(), any());
        doReturn(mockVal).when(mockValFactory).getHelper();
        doReturn(new HashSet<>()).when(mockVal).getProblems();
        doReturn(equipment).when(mockVal).checkEquipment(any(), any(), anyBoolean());

        doReturn(opres).when(service).record(any(), any(), any(), any(), any(), any());

        AnalyserRequest request = new AnalyserRequest("opname", "lotA", "lotB", "run", LocalDateTime.now(), List.of(new AnalyserLabware()), equipment.getId());

        assertSame(opres, service.perform(user, request));

        verifyValidation(request, opType, lwMap, priorOps, mockVal);
        verify(service).record(user, request, opType, lwMap, workMap, equipment);
    }

    @Test
    public void testPerform_problems() {
        User user = EntityFactory.getUser();
        final Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        Map<Integer, Operation> priorOps = Map.of(5, new Operation());
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));

        doAnswer(addProblem("bad lw", lwMap)).when(service).checkLabware(any(), any());
        doAnswer(addProblem("bad optype", opType)).when(service).checkOpType(any(), any());
        doAnswer(addProblem("bad prior ops", priorOps)).when(service).loadPriorOps(any(), any(), any());
        doAnswer(addProblem("bad time")).when(service).checkTimestamp(any(), any(), any(), any());
        doAnswer(addProblem("bad work", workMap)).when(service).loadWork(any(), any());
        doAnswer(addProblem("bad position")).when(service).checkCassettePositions(any(), any());
        doAnswer(addProblem("bad roi")).when(service).checkRois(any(), any());
        doAnswer(addProblem("bad sample")).when(service).checkSamples(any(), any(), any());
        doAnswer(addProblem("bad lot")).when(service).validateLot(any(), any());
        doAnswer(addProblem("bad run")).when(service).validateRunName(any(), any());
        doReturn(mockVal).when(mockValFactory).getHelper();
        doReturn(Set.of("Bad equipment")).when(mockVal).getProblems();

        AnalyserRequest request = new AnalyserRequest("opname", "lotA", "lotB", "run", LocalDateTime.now(), List.of(new AnalyserLabware()), null);

        assertValidationException(() -> service.perform(user, request), List.of(
                        "bad lw", "bad optype", "bad prior ops", "bad time", "bad work",
                        "bad position", "bad roi", "bad sample", "bad lot", "bad run", "Bad equipment"
        ));
        verify(mockValFactory, times(1)).getHelper();
        verifyValidation(request, opType, lwMap, priorOps, mockVal);

        verify(service, never()).record(any(), any(), any(), any(), any(), any());
    }

    private void verifyValidation(AnalyserRequest request, OperationType opType, UCMap<Labware> lwMap,
                                  Map<Integer, Operation> priorOps, ValidationHelper mockVal) {
        verify(service).checkLabware(any(), same(request.getLabware()));
        verify(service).checkOpType(any(), same(request.getOperationType()));
        verify(service).loadPriorOps(any(), same(opType), eq(lwMap.values()));
        verify(service).checkTimestamp(any(), same(request.getPerformed()), same(priorOps), eq(lwMap.values()));
        verify(service).loadWork(any(), same(request.getLabware()));
        verify(service).checkCassettePositions(any(), same(request.getLabware()));
        verify(service).checkRois(any(), same(request.getLabware()));
        verify(service).checkSamples(any(), same(request.getLabware()), same(lwMap));
        verify(service).validateLot(any(), eq(request.getLotNumberA()));
        verify(service).validateLot(any(), eq(request.getLotNumberB()));
        verify(service).validateRunName(any(), eq(request.getRunName()));
        verify(mockVal).checkEquipment(request.getEquipmentId(), EQUIPMENT_CATEGORY, true);
    }

    @ParameterizedTest
    @ValueSource(strings={
            "STAN-A1,STAN-A2",
            "null,STAN-A2",
            "STAN-A1,",
            ""
    })
    public void testCheckLabware(String joinedBarcodes) {
        List<AnalyserLabware> als;
        List<String> bcs;
        String expectedError;
        if (nullOrEmpty(joinedBarcodes)) {
            als = List.of();
            bcs = List.of();
            expectedError = "No labware specified.";
        } else {
            String[] splitBarcodes = joinedBarcodes.split(",");
            for (int i = 0; i < splitBarcodes.length; ++i) {
                if (splitBarcodes[i].equals("null")) {
                    splitBarcodes[i] = null;
                }
            }
            als = Arrays.stream(splitBarcodes)
                    .map(bc -> new AnalyserLabware(bc, null, null, null))
                    .collect(toList());
            bcs = Arrays.stream(splitBarcodes)
                    .filter(bc -> bc!=null && !bc.isEmpty())
                    .collect(toList());
            expectedError = (bcs.size()==als.size() ? null : "Labware barcode missing.");
        }
        final List<String> problems = new ArrayList<>(expectedError==null ? 0 : 1);

        if (!bcs.isEmpty()) {
            UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
            doReturn(lwMap).when(service).loadLabware(any(), any());

            assertSame(lwMap, service.checkLabware(problems, als));
            verify(service).loadLabware(same(problems), eq(bcs));
        } else {
            assertThat(service.checkLabware(problems, als)).isEmpty();
            verify(service, never()).loadLabware(any(), any());
        }
        assertProblem(problems, expectedError);
    }

    @ParameterizedTest
    @CsvSource({
            "xenium analyser,true,,",
            ",,,No operation type specified.",
            "Xenium Analyser,false,,The specified operation type Xenium Analyser cannot be used in this operation.",
            "Xenium analyser,true,Bad optype,Bad optype",
            "Foozle,true,,The specified operation type Foozle cannot be used in this operation.",

    })
    public void testCheckOpType(String opName, Boolean inPlace, String superProblem, String expectedProblem) {
        OperationType opType = inPlace == null ? null :
                inPlace ? EntityFactory.makeOperationType(opName, null, OperationTypeFlag.IN_PLACE)
                        : EntityFactory.makeOperationType(opName, null);
        mayAddProblem(superProblem, opType).when(service).loadOpType(any(), any());

        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(opType, service.checkOpType(problems, opName));
        assertProblem(problems, expectedProblem);
        if (!nullOrEmpty(opName)) {
            verify(service).loadOpType(any(), same(opName));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "xenium Analyser,Probe hybridisation Xenium",
            ",",
            "Foozle,"
    })
    public void testPrecedingOpTypeName(String opName, String expected) {
        OperationType opType = (opName==null ? null : EntityFactory.makeOperationType(opName, null));
        assertEquals(expected, service.precedingOpTypeName(opType));
    }

    @ParameterizedTest
    @CsvSource({
            "Foo,true,,",
            "Foo,false,,Operation type Foo is missing from the database.",
            ",false,,",
            "Foo,true,Bananas,Bananas",
    })
    public void testLoadPriorOps(String priorOpName, boolean exists, String lookupProblem, String expectedProblem) {
        OperationType opType = EntityFactory.makeOperationType("...", null);
        doReturn(priorOpName).when(service).precedingOpTypeName(opType);
        OperationType priorOpType = (exists ? EntityFactory.makeOperationType(priorOpName, null) : null);
        when(mockOpTypeRepo.findByName(priorOpName)).thenReturn(Optional.ofNullable(priorOpType));
        Map<Integer, Operation> opMap;
        if (priorOpName==null || !exists) {
            opMap = Map.of();
        } else {
            opMap = Map.of(3, new Operation());
            mayAddProblem(lookupProblem, opMap).when(service).lookUpLatestOps(any(), any(), any(), anyBoolean());
        }
        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        final List<Labware> labware = List.of(EntityFactory.getTube());
        assertEquals(opMap, service.loadPriorOps(problems, opType, labware));
        assertProblem(problems, expectedProblem);
        if (priorOpType!=null) {
            verify(service).lookUpLatestOps(problems, priorOpType, labware, true);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "3,3,2,",
            "4,5,2,",
            "4,5,,",
            "3,2,,The given date is in the future.",
            "3,5,4,The given date is before the preceding operation for labware [STAN-A1].",
    })
    public void testCheckTimestamp(int tsIndex, int nowIndex, Integer priorOpIndex, String expectedProblem) {
        setMockClock(mockClock, LocalDateTime.of(2023,1,nowIndex,12,0));
        LocalDateTime ts = LocalDateTime.of(2023,1,tsIndex, 12,0);
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        lw.setBarcode("STAN-A1");
        Map<Integer, Operation> priorOps;
        if (priorOpIndex!=null) {
            Operation op = new Operation();
            op.setPerformed(LocalDateTime.of(2023,1,priorOpIndex, 12,0));
            priorOps = Map.of(lw.getId(), op);
        } else {
            priorOps = Map.of();
        }

        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.checkTimestamp(problems, ts, priorOps, List.of(lw));
        assertProblem(problems, expectedProblem);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadWork(boolean anyProblem) {
        String workProblem = (anyProblem ? "bad work" : null);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        mayAddProblem(workProblem, workMap).when(mockWorkService).validateUsableWorks(any(), any());
        List<String> workNumbers = List.of("SGP1", "SGP2");
        List<AnalyserLabware> als = workNumbers.stream()
                .map(wn -> new AnalyserLabware(null, wn, null, null))
                .collect(toList());
        List<String> problems = new ArrayList<>(anyProblem ? 1 : 0);
        assertSame(workMap, service.loadWork(problems, als));
        assertProblem(problems, workProblem);
        verify(mockWorkService).validateUsableWorks(problems, workNumbers);
    }

    @ParameterizedTest
    @CsvSource({
            "left-right,",
            "right-left,",
            "right-right-left,Cassette position specified multiple times: [right]",
            "right-left-,Cassette position not specified.",
    })
    public void testCheckCassettePositions(String positionsJoined, String expectedProblem) {
        String[] positionsSplit = positionsJoined.split("-", -1);
        List<AnalyserLabware> als = Arrays.stream(positionsSplit)
                .map(s -> s.isEmpty() ? null : CassettePosition.valueOf(s))
                .map(pos -> new AnalyserLabware(null, null, pos, null))
                .collect(toList());
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.checkCassettePositions(problems, als);
        assertProblem(problems, expectedProblem);
    }

    @ParameterizedTest
    @CsvSource({
            "alpha;beta,",
            "alpha;;beta,ROI not specified.",
            "alpha;;beta;,ROI not specified.",
            "alpha;beta;gamma!;Gamma!;delta,Bad: gamma!",
    })
    public void testCheckRois(String roisJoined, String expectedProblem) {
        String[] roisSplit = roisJoined.split(";", -1);
        List<SampleROI> srs = Arrays.stream(roisSplit)
                .map(s -> new SampleROI(null, null, s))
                .collect(toList());
        List<AnalyserLabware> als = List.of(new AnalyserLabware(null, null, null, srs));

        doAnswer(invocation -> {
            String string = invocation.getArgument(0);
            if (string.indexOf('!') < 0) {
                return true;
            }
            Consumer<String> addProblem = invocation.getArgument(1);
            addProblem.accept("Bad: "+string);
            return false;
        }).when(mockRoiValidator).validate(any(), any());

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.checkRois(problems, als);
        assertProblem(problems, expectedProblem);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCheckSamples(boolean anyProblem) {
        Sample[] sams = IntStream.rangeClosed(1,2)
                .mapToObj(i -> new Sample(i, null, EntityFactory.getTissue(), EntityFactory.getBioState()))
                .toArray(Sample[]::new);
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw1 = EntityFactory.makeLabware(lt, sams[0], sams[0]);
        lw1.getFirstSlot().addSample(sams[1]);

        Labware lw2 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sams[0]);
        lw1.setBarcode("STAN-1");
        lw2.setBarcode("STAN-2");
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        List<SampleROI> srs1 = List.of(
                new SampleROI(A1, sams[0].getId(), null),
                new SampleROI(A1, sams[1].getId(), null),
                new SampleROI(A2, sams[0].getId(), null)
        );
        List<SampleROI> srs2;
        if (anyProblem) {
            srs2 = List.of(
                    new SampleROI(null, 404, null),
                    new SampleROI(A2, 405, null),
                    new SampleROI(A1, 406, null),
                    new SampleROI(A1, null, null),
                    new SampleROI(A1, sams[0].getId(), "Alpha"),
                    new SampleROI(A1, sams[0].getId(), "Beta")
            );
        } else {
            srs2 = List.of(new SampleROI(A1, sams[0].getId(), null));
        }
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        List<AnalyserLabware> als = List.of(
                new AnalyserLabware(lw1.getBarcode(), null, null, srs1),
                new AnalyserLabware(lw2.getBarcode(), null, null, srs2)
        );
        final List<String> problems = new ArrayList<>(anyProblem ? 5 : 0);
        service.checkSamples(problems, als, lwMap);
        verify(service).checkLabwareSamples(problems, lw1, srs1);
        verify(service).checkLabwareSamples(problems, lw2, srs2);

        if (anyProblem) {
            assertThat(problems).containsExactlyInAnyOrder(
                    "Address not specified in request.",
                    "Sample id not specified in request.",
                    "No such slot in labware STAN-2: [A2]",
                    "Sample id not present in specified slot of STAN-2: [{sampleId: 406, address: A1}]",
                    "Sample and slot specified multiple times for labware STAN-2: [{sampleId: 1, address: A1}]"
            );
        } else {
            assertThat(problems).isEmpty();
        }
    }

    @Test
    public void testCheckLabwareSamples_none() {
        final List<String> problems = new ArrayList<>(1);
        Labware lw = EntityFactory.getTube();
        service.checkLabwareSamples(problems, lw, List.of());
        assertProblem(problems, "No ROIs specified for labware "+lw.getBarcode()+".");
    }

    @Test
    public void testCheckLabwareSamples_good() {
        Sample[] samples = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> new Sample(i, null, EntityFactory.getTissue(), EntityFactory.getBioState()))
                .toArray(Sample[]::new);
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw = EntityFactory.makeLabware(lt, samples);
        lw.getFirstSlot().addSample(samples[1]);
        lw.setBarcode("STAN-1");
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        List<SampleROI> srs = List.of(
                new SampleROI(A1, samples[0].getId(), null),
                new SampleROI(A1, samples[1].getId(), null),
                new SampleROI(A2, samples[1].getId(), null)
        );
        final List<String> problems = new ArrayList<>(0);
        service.checkLabwareSamples(problems, lw, srs);
        assertThat(problems).isEmpty();
    }

    @Test
    public void testCheckLabwareSamples_bad() {
        Sample sam = new Sample(1, null, EntityFactory.getTissue(), EntityFactory.getBioState());
        Labware lw = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam);
        lw.setBarcode("STAN-1");
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final Address A3 = new Address(1,3);
        int samId = sam.getId();
        List<SampleROI> srs = List.of(
                new SampleROI(null, samId, null),
                new SampleROI(A2, samId, null),
                new SampleROI(A3, 505, null),
                new SampleROI(A1, 404, null),
                new SampleROI(A1, null, null),
                new SampleROI(A1, samId, null),
                new SampleROI(A1, samId, null)
        );
        final List<String> problems = new ArrayList<>(0);
        service.checkLabwareSamples(problems, lw, srs);
        assertThat(problems).containsExactlyInAnyOrder(
                "Address not specified in request.",
                "Sample id not specified in request.",
                "No such slots in labware STAN-1: [A2, A3]",
                "Sample id not present in specified slot of STAN-1: [{sampleId: 404, address: A1}]",
                "Sample and slot specified multiple times for labware STAN-1: [{sampleId: 1, address: A1}]"
        );
    }

    @ParameterizedTest
    @ValueSource(strings={"", "X!", "Alpha"})
    public void testValidateLot(String string) {
        String expectedProblem;
        if (string==null || string.isEmpty()) {
            expectedProblem = "Missing lot number.";
        } else if (string.indexOf('!') >= 0) {
            expectedProblem = "Bad "+string;
            doAnswer(invocation -> {
                String v = invocation.getArgument(0);
                Consumer<String> cons = invocation.getArgument(1);
                cons.accept("Bad "+v);
                return false;
            }).when(mockDecodingReagentLotValidator).validate(any(), any());
        } else {
            expectedProblem = null;
            doReturn(true).when(mockDecodingReagentLotValidator).validate(any(), any());
        }
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.validateLot(problems, string);
        assertProblem(problems, expectedProblem);
    }

    @ParameterizedTest
    @CsvSource({
            "run1,true,false,",
            ",true,false,Missing run name.",
            "run1,false,false,Bad: run1",
            "run1,true,true,Run name already used: \"run1\"",
    })
    public void testValidateRunName(String string, boolean valid, boolean exists, String expectedProblem) {
        if (valid) {
            doReturn(true).when(mockRunNameValidator).validate(any(), any());
        } else {
            doAnswer(invocation -> {
                String s = invocation.getArgument(0);
                Consumer<String> cons = invocation.getArgument(1);
                cons.accept("Bad: "+s);
                return false;
            }).when(mockRunNameValidator).validate(any(), any());
        }
        doReturn(exists).when(mockLwNoteRepo).existsByNameAndValue("run", string);

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.validateRunName(problems, string);
        assertProblem(problems, expectedProblem);
    }

    @Test
    public void testRecord() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("foo", null);
        LabwareType lt = EntityFactory.getTubeType();
        Sample sam = new Sample(1, null, EntityFactory.getTissue(), EntityFactory.getBioState());
        Labware lw1 = EntityFactory.makeLabware(lt, sam);
        Labware lw2 = EntityFactory.makeLabware(lt, sam);
        lw1.setBarcode("STAN-1");
        lw2.setBarcode("STAN-2");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        Work work1 = EntityFactory.makeWork("SGP1");
        Work work2 = EntityFactory.makeWork("SGP2");
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work1, work2);
        final Address A1 = new Address(1,1);
        List<SampleROI> srs1 = List.of(new SampleROI(A1, 1, "roi1"));
        List<SampleROI> srs2 = List.of(new SampleROI(A1, 2, "roi2"));
        AnalyserLabware al1 = new AnalyserLabware("STAN-1", "SGP1", CassettePosition.left, srs1);
        AnalyserLabware al2 = new AnalyserLabware("STAN-2", "SGP2", CassettePosition.right, srs2);
        Equipment equipment = new Equipment(1, "Xenium 1", EQUIPMENT_CATEGORY, true);
        AnalyserRequest request = new AnalyserRequest(opType.getName(), "lot1", "lot2", "run1",
                LocalDateTime.of(2023,1,1,12,0), List.of(al1, al2), equipment.getId());
        Operation op1 = new Operation();
        op1.setId(11);
        Operation op2 = new Operation();
        op2.setId(12);
        when(mockOpService.createOperationInPlace(same(opType), same(user), same(lw1), isNull(), any())).thenReturn(op1);
        when(mockOpService.createOperationInPlace(same(opType), same(user), same(lw2), isNull(), any())).thenReturn(op2);

        OperationResult opres = service.record(user, request, opType, lwMap, workMap, equipment);

        assertThat(opres.getLabware()).containsExactly(lw1, lw2);
        assertThat(opres.getOperations()).containsExactly(op1, op2);

        verify(mockLwNoteRepo).saveAll(sameElements(List.of(
                new LabwareNote(null, lw1.getId(), op1.getId(), "run", "run1"),
                new LabwareNote(null, lw2.getId(), op2.getId(), "run", "run1"),
                new LabwareNote(null, lw1.getId(), op1.getId(), "decoding reagent A lot", "lot1"),
                new LabwareNote(null, lw2.getId(), op2.getId(), "decoding reagent A lot", "lot1"),
                new LabwareNote(null, lw1.getId(), op1.getId(), "decoding reagent B lot", "lot2"),
                new LabwareNote(null, lw2.getId(), op2.getId(), "decoding reagent B lot", "lot2"),
                new LabwareNote(null, lw1.getId(), op1.getId(), "cassette position", "left"),
                new LabwareNote(null, lw2.getId(), op2.getId(), "cassette position", "right")
        ), true));

        verify(mockWorkService).link(work1, List.of(op1));
        verify(mockWorkService).link(work2, List.of(op2));
        assertSame(request.getPerformed(), op1.getPerformed());
        assertSame(request.getPerformed(), op2.getPerformed());
        verify(mockOpRepo).saveAll(List.of(op1, op2));
        final Integer slot1id = lw1.getFirstSlot().getId();
        final Integer slot2id = lw2.getFirstSlot().getId();
        verify(mockRoiRepo).saveAll(List.of(
                new Roi(slot1id, 1, op1.getId(), "roi1"),
                new Roi(slot2id, 2, op2.getId(), "roi2")
        ));
        verify(service).addRois(any(), eq(op1.getId()), same(lw1), same(srs1));
        verify(service).addRois(any(), eq(op2.getId()), same(lw2), same(srs2));
    }

    @Test
    public void testAddRois() {
        Integer opId = 50;
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        List<SampleROI> srs = List.of(
                new SampleROI(A1, 1, "roi1"),
                new SampleROI(A2, 2, "roi2")
        );
        final List<Roi> rois = new ArrayList<>(srs.size());
        service.addRois(rois, opId, lw, srs);
        assertThat(rois).containsExactly(
                new Roi(lw.getSlot(A1).getId(), 1, opId, "roi1"),
                new Roi(lw.getSlot(A2).getId(), 2, opId, "roi2")
        );
    }
}