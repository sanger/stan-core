package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.EntityFactory.objToCollection;
import static uk.ac.sanger.sccp.stan.service.OpWithSlotMeasurementsServiceImp.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.coalesce;

/**
 * Tests {@link OpWithSlotMeasurementsServiceImp}
 */
public class TestOpWithSlotMeasurementsService {
    private OperationTypeRepo mockOpTypeRepo;
    private MeasurementRepo mockMeasRepo;
    private LabwareRepo mockLwRepo;
    private OperationCommentRepo mockOpComRepo;
    private LabwareValidatorFactory mockLwValFactory;
    private Sanitiser<String> mockCqSan;
    private Sanitiser<String> mockConcSan;
    private WorkService mockWorkService;
    private OperationService mockOpService;
    private CommentValidationService mockCommentValidationService;

    private OpWithSlotMeasurementsServiceImp service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockMeasRepo = mock(MeasurementRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockOpComRepo = mock(OperationCommentRepo.class);
        mockLwValFactory = mock(LabwareValidatorFactory.class);
        mockCqSan = mock(Sanitiser.class);
        mockConcSan = mock(Sanitiser.class);
        mockWorkService = mock(WorkService.class);
        mockOpService = mock(OperationService.class);
        mockCommentValidationService = mock(CommentValidationService.class);

        service = spy(new OpWithSlotMeasurementsServiceImp(mockOpTypeRepo, mockMeasRepo, mockLwRepo, mockOpComRepo,
                mockLwValFactory, mockCqSan, mockConcSan, mockWorkService, mockOpService, mockCommentValidationService));
    }

    @Test
    public void testPerform_valid() {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        OperationType opType = EntityFactory.makeOperationType("Amp", null, OperationTypeFlag.IN_PLACE);
        Work work = new Work(10, "SGP10", null, null, null, null, Work.Status.active);
        final Address A1 = new Address(1,1);
        OpWithSlotMeasurementsRequest request = new OpWithSlotMeasurementsRequest(lw.getBarcode(), opType.getName(), work.getWorkNumber(),
                List.of(new SlotMeasurementRequest(A1, "CDNA CONCENTRATION", "10", null)));
        List<SlotMeasurementRequest> sanMeas = List.of(new SlotMeasurementRequest(A1, "cDNA concentration", "10.000", null));
        stubValidation(lw, opType, work, sanMeas, null);
        List<Comment> comments = List.of(new Comment(50, "Banana", "custard"));
        doReturn(comments).when(service).validateComments(any(), any());

        OperationResult opres = new OperationResult(List.of(), List.of(lw));
        doReturn(opres).when(service).execute(any(), any(), any(), any(), any(), any());

        assertSame(opres, service.perform(user, request));
        verifyValidation(lw, opType, request, sanMeas);

        verify(service).execute(user, lw, opType, work, comments, sanMeas);
    }

    @Test
    public void testPerform_null() {
        assertValidationError(() -> service.perform(null, null), "No user specified.", "No request specified.");
        verify(service, never()).validateLabware(any(), any());
        verify(service, never()).loadOpType(any(), any());
        verifyNoInteractions(mockWorkService);
        verify(service, never()).validateAddresses(any(), any(), any());
        verify(service, never()).sanitiseMeasurements(any(), any(), any());
        verify(service, never()).checkForDupeMeasurements(any(), any());
        verify(service, never()).validateComments(any(), any());
        verify(service, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testPerform_invalid() {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        OperationType opType = EntityFactory.makeOperationType("Amp", null, OperationTypeFlag.IN_PLACE);
        Work work = new Work(10, "SGP10", null, null, null, null, Work.Status.active);
        final Address A1 = new Address(1,1);
        OpWithSlotMeasurementsRequest request = new OpWithSlotMeasurementsRequest(lw.getBarcode(), opType.getName(), work.getWorkNumber(),
                List.of(new SlotMeasurementRequest(A1, "CDNA CONCENTRATION", "10", null)));
        List<SlotMeasurementRequest> sanMeas = List.of(new SlotMeasurementRequest(A1, "cDNA concentration", "10.000", null));
        final String problem = "Everything is bad.";
        stubValidation(lw, opType, work, sanMeas, problem);

        assertValidationError(() -> service.perform(user, request), problem);
        verifyValidation(lw, opType, request, sanMeas);

        verify(service, never()).execute(any(), any(), any(), any(), any(), any());
    }

    private void stubValidation(Labware lw, OperationType opType, Work work, List<SlotMeasurementRequest> sanMeas,
                                String problem) {
        doReturn(lw).when(service).validateLabware(any(), any());
        doReturn(opType).when(service).loadOpType(any(), any());
        doReturn(work).when(mockWorkService).validateUsableWork(any(), any());
        doNothing().when(service).validateAddresses(any(), any(), any());
        doReturn(sanMeas).when(service).sanitiseMeasurements(any(), any(), any());
        doReturn(List.of()).when(service).validateComments(any(), any());
        if (problem==null) {
            doNothing().when(service).checkForDupeMeasurements(any(), any());
        } else {
            doAnswer(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add(problem);
                return null;
            }).when(service).checkForDupeMeasurements(any(), any());
        }
    }

    private static void assertValidationError(Executable executable, String... problems) {
        ValidationException ex = assertThrows(ValidationException.class, executable);
        //noinspection unchecked
        assertThat((Collection<Object>) ex.getProblems()).containsExactlyInAnyOrder(problems);
    }

    private void verifyValidation(Labware lw, OperationType opType, OpWithSlotMeasurementsRequest request, List<SlotMeasurementRequest> sanMeas) {
        //noinspection unchecked
        ArgumentCaptor<Collection<String>> problemsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(service).validateLabware(problemsCaptor.capture(), eq(request.getBarcode()));
        Collection<String> problems = problemsCaptor.getValue();
        verify(service).loadOpType(same(problems), eq(request.getOperationType()));
        verify(mockWorkService).validateUsableWork(same(problems), eq(request.getWorkNumber()));
        verify(service).validateAddresses(same(problems), same(lw), same(request.getSlotMeasurements()));
        verify(service).sanitiseMeasurements(same(problems), same(opType), same(request.getSlotMeasurements()));
        verify(service).validateComments(same(problems), same(request.getSlotMeasurements()));
        verify(service).checkForDupeMeasurements(same(problems), same(sanMeas));
    }

    @ParameterizedTest
    @MethodSource("validateLabwareArgs")
    public void testValidateLabware(String barcode, Labware lw, String valError, String expectedProblem) {
        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        if (barcode==null || barcode.isEmpty()) {
            assertNull(service.validateLabware(problems, barcode));
            verifyNoInteractions(mockLwValFactory);
            assertThat(problems).containsExactly(expectedProblem);
            return;
        }
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        when(val.loadLabware(any(), any())).thenReturn(lw==null ? List.of() : List.of(lw));
        when(val.getErrors()).thenReturn(valError==null ? List.of() : List.of(valError));

        assertSame(lw, service.validateLabware(problems, barcode));
        verify(val).loadLabware(mockLwRepo, List.of(barcode));
        verify(val).validateSources();
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    static Stream<Arguments> validateLabwareArgs() {
        Labware lw = EntityFactory.getTube();
        String bc = lw.getBarcode();
        return Arrays.stream(new Object[][] {
                {bc, lw, null, null},
                {"STAN-404", null, "Unknown labware", "Unknown labware"},
                {"", null, null, "No barcode specified."},
                {null, null, null, "No barcode specified."},
                {bc, lw, "Bad labware.", "Bad labware."},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @CsvSource({
            "cDNA amplification,",
            "cDNA analysis,",
            "Bake, Operation not expected for this request: Bake",
            "'', No operation type specified.",
            ", No operation type specified.",
            "Bananas, Unknown operation type: \"Bananas\"",
            "Transfer, Operation cannot be recorded in place: Transfer",
    })
    public void testLoadOpType(String opName, String expectedProblem) {
        OperationType opType;
        switch (coalesce(opName, "")) {
            case OP_CDNA_ANALYSIS: case OP_CDNA_AMP: case "Bake":
                opType = EntityFactory.makeOperationType(opName, null, OperationTypeFlag.IN_PLACE);
                break;
            case "Transfer":
                opType = EntityFactory.makeOperationType(opName, null);
                break;
            default: opType = null;
        }
        if (opName!=null && !opName.isEmpty()) {
            when(mockOpTypeRepo.findByName(opName)).thenReturn(Optional.ofNullable(opType));
        }
        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(opType, service.loadOpType(problems, opName));
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    @ParameterizedTest
    @MethodSource("validateAddressesArgs")
    public void testValidateAddresses(Labware lw, Collection<Address> addresses, Collection<String> expectedProblems) {
        List<SlotMeasurementRequest> srms = addresses.stream()
                .map(ad -> new SlotMeasurementRequest(ad, "Alpha", "Beta", null))
                .collect(toList());
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.validateAddresses(problems, lw, srms);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateAddressesArgs() {
        LabwareType lt = EntityFactory.makeLabwareType(2,3);
        Sample sam = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(lt, sam, sam, sam);
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final Address B2 = new Address(2,2);
        final Address B3 = new Address(2,3);
        final Address C3 = new Address(3,3);
        final Address C4 = new Address(3,4);
        return Arrays.stream(new Object[][]{
                {null, null},
                {A1, null},
                {List.of(A1,A2), null},
                {B3, "Slot is empty: [B3]"},
                {List.of(A1,A2,B2,B3), "Slots are empty: [B2, B3]"},
                {Arrays.asList(A1, null), "Missing address for measurement."},
                {List.of(A1, A2, C4), "Invalid address for labware: [C4]"},
                {List.of(C3, C4), "Invalid addresses for labware: [C3, C4]"},
                {Arrays.asList(A1,B2,C3,null),
                 List.of("Missing address for measurement.", "Invalid address for labware: [C3]", "Slot is empty: [B2]")},
        }).map(arr -> Arguments.of(lw, objToCollection(arr[0]), objToCollection(arr[1])));
    }

    @ParameterizedTest
    @MethodSource("sanitiseMeasurementsArgs")
    public void testSanitiseMeasurements(Answer<SlotMeasurementRequest> sanSmrMock,
                                         OperationType opType,
                                         Collection<SlotMeasurementRequest> smrs,
                                         Collection<SlotMeasurementRequest> expectedSan,
                                         Collection<String> expectedProblems) {
        doAnswer(sanSmrMock).when(service).sanitiseMeasurement(any(), any(), any(), any());
        final Set<String> problems = new HashSet<>();
        assertThat(service.sanitiseMeasurements(problems, opType, smrs)).containsExactlyInAnyOrderElementsOf(expectedSan);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> sanitiseMeasurementsArgs() {
        Answer<SlotMeasurementRequest> sanSmrMock = invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            Collection<String> invalidNames = invocation.getArgument(1);
            OperationType opType = invocation.getArgument(2);
            SlotMeasurementRequest smr = invocation.getArgument(3);
            boolean ok = true;
            if (smr.getName()==null) {
                problems.add("Missing name for measurement.");
                ok = false;
            } else if (smr.getName().indexOf('!')>=0 && opType!=null) {
                invalidNames.add(smr.getName());
                ok = false;
            }
            if (smr.getValue()==null) {
                problems.add("Missing value for measurement.");
                ok = false;
            } else if (smr.getValue().indexOf('!')>=0 && opType!=null) {
                problems.add("Invalid value: "+smr.getValue());
                ok = false;
            }
            if (ok && smr.getAddress()!=null && opType!=null) {
                return new SlotMeasurementRequest(smr.getAddress(), smr.getName()+"san", smr.getValue()+"san", null);
            }
            return null;
        };

        OperationType opType = EntityFactory.makeOperationType(OP_CDNA_AMP, null, OperationTypeFlag.IN_PLACE);
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        return Arrays.stream(new Object[][] {
                {opType, null, null, null},
                {opType, new SlotMeasurementRequest(A1, "Alpha", "10", null), new SlotMeasurementRequest(A1, "Alphasan", "10san", null), null},
                {opType, new SlotMeasurementRequest(null, "Alpha", "10", null), null, null},
                {null, new SlotMeasurementRequest(A1, "Alpha", "10", null), null, null},
                {opType, List.of(
                        new SlotMeasurementRequest(A1, "Alpha", "10", null),
                        new SlotMeasurementRequest(A2, "Beta", "20", null)
                    ),
                    List.of(
                        new SlotMeasurementRequest(A1, "Alphasan", "10san", null),
                        new SlotMeasurementRequest(A2, "Betasan", "20san", null)
                    ),null
                },
                {opType, new SlotMeasurementRequest(A1, "Alpha", null, null), null, "Missing value for measurement."},
                {opType, new SlotMeasurementRequest(A1, null, "10", null), null, "Missing name for measurement."},
                {opType, new SlotMeasurementRequest(A1, "Alpha", "10!", null), null, "Invalid value: 10!"},
                {opType, new SlotMeasurementRequest(A1, "Alpha!", "10", null), null, "Unexpected measurements given for operation "+OP_CDNA_AMP+": [Alpha!]"},
                {opType, List.of(
                        new SlotMeasurementRequest(A1, "Alpha", "10", null),
                        new SlotMeasurementRequest(A1, "Alpha!", "20", null),
                        new SlotMeasurementRequest(A1, "Beta!", "20", null),
                        new SlotMeasurementRequest(null, "Beta", "30", null),
                        new SlotMeasurementRequest(A1, "Gamma", null, null),
                        new SlotMeasurementRequest(A1, "Delta", "40!", null),
                        new SlotMeasurementRequest(A1, null, "40", null),
                        new SlotMeasurementRequest(A2, "Epsilon", "50", null)
                    ),
                    List.of(
                        new SlotMeasurementRequest(A1, "Alphasan", "10san", null),
                        new SlotMeasurementRequest(A2, "Epsilonsan", "50san", null)
                        ),
                    List.of("Unexpected measurements given for operation "+OP_CDNA_AMP+": [Alpha!, Beta!]", "Invalid value: 40!",
                            "Missing name for measurement.", "Missing value for measurement.")
                },
        }).map(arr -> Arguments.of(sanSmrMock, arr[0], objToCollection(arr[1]), objToCollection(arr[2]), objToCollection(arr[3])));
    }

    @ParameterizedTest
    @MethodSource("sanitiseMeasurementArgs")
    public void testSanitiseMeasurement(SlotMeasurementRequest smr, OperationType opType, String sanName, boolean invalidName,
                                        String sanValue, String valueError, Collection<String> expectedProblems,
                                        SlotMeasurementRequest expectedSmr) {
        doReturn(sanName).when(service).sanitiseMeasurementName(opType, smr.getName());
        if (valueError!=null) {
            doAnswer(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add(valueError);
                return sanValue;
            }).when(service).sanitiseMeasurementValue(any(), eq(sanName), eq(smr.getValue()));
        } else if (sanName!=null && opType!=null) {
            doReturn(sanValue).when(service).sanitiseMeasurementValue(any(), eq(sanName), eq(smr.getValue()));
        }
        final List<String> problems = new ArrayList<>();
        final Set<String> invalidNames = new HashSet<>(sanName==null ? 1 : 0);
        assertEquals(expectedSmr, service.sanitiseMeasurement(problems, invalidNames, opType, smr));

        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        if (invalidName) {
            assertThat(invalidNames).containsExactly(smr.getName());
        } else {
            assertThat(invalidNames).isEmpty();
        }
    }

    // Cases:
    // * null/empty name
    // * null/empty value
    // * no op-type
    // * invalid name
    // * invalid value
    // * no address
    // * multiple problems
    // * valid (with modifications)
    static Stream<Arguments> sanitiseMeasurementArgs() {
        final Address A1 = new Address(1,1);
        final String BAD_VALUE = "Bad value!";
        OperationType opType = EntityFactory.makeOperationType(OP_CDNA_AMP, null, OperationTypeFlag.IN_PLACE);
        return Arrays.stream(new Object[][] {
                {new SlotMeasurementRequest(A1, null, "10", null), opType, null, false, "10", null, "Missing name for measurement.", null},
                {new SlotMeasurementRequest(A1, "", "10", null), opType, null, false, "10", null, "Missing name for measurement.", null},
                {new SlotMeasurementRequest(A1, "Alpha", null, null), opType, "Alpha", false, null, null, "Missing value for measurement.", null},
                {new SlotMeasurementRequest(A1, "Alpha", "", null), opType, "Alpha", false, null, null, "Missing value for measurement.", null},
                {new SlotMeasurementRequest(A1, "Alpha", "10", null), null, null, false, "10", null, null, null},
                {new SlotMeasurementRequest(A1, "Alpha!", "10", null), opType, null, true, "10", null, null, null},
                {new SlotMeasurementRequest(A1, "Alpha", "10!", null), opType, "Alpha", false, null, BAD_VALUE, BAD_VALUE, null},
                {new SlotMeasurementRequest(null, "Alpha", "10", null), opType, "Alpha", false, "10", null, null, null},
                {new SlotMeasurementRequest(null, null, null, null), null, null, false, null, null,
                    List.of("Missing name for measurement.", "Missing value for measurement."), null},
                {new SlotMeasurementRequest(A1, "Alpha!", "10!", null), opType, "Alpha", false, "10", null, null,
                    new SlotMeasurementRequest(A1, "Alpha", "10", null)},
        }).map(arr -> Arguments.of(arr[0], arr[1], arr[2], arr[3], arr[4], arr[5], objToCollection(arr[6]), arr[7]));
    }

    @ParameterizedTest
    @CsvSource({
            OP_CDNA_AMP+",Cq value,Cq value",
            OP_CDNA_AMP+",CQ VALUE,Cq value",
            OP_CDNA_ANALYSIS+",cDNA concentration,cDNA concentration",
            OP_CDNA_ANALYSIS+",CDNA CONCENTRATION,cDNA concentration",
            OP_CDNA_ANALYSIS+",library concentration,Library concentration",
            OP_CDNA_ANALYSIS+",LIBRARY CONCENTRATION,Library concentration",
            OP_CDNA_AMP+",cDNA concentration,",
            OP_CDNA_ANALYSIS+",Cq value,",
            ",Cq value,",
            ",cDNA concentration,",
            "Bananas,Cq value,",
            OP_CDNA_ANALYSIS+",Custard,",
    })
    public void testSanitiseMeasurementName(String opTypeName, String name, String expected) {
        OperationType opType = (opTypeName==null ? null : EntityFactory.makeOperationType(opTypeName, null, OperationTypeFlag.IN_PLACE));
        assertEquals(expected, service.sanitiseMeasurementName(opType, name));
    }

    @Test
    public void testValidateComments() {
        Address A1 = new Address(1,1);
        List<SlotMeasurementRequest> sms = List.of(
                new SlotMeasurementRequest(A1, "Alpha", "Beta", 1),
                new SlotMeasurementRequest(A1, "Alpha", "Beta", 2),
                new SlotMeasurementRequest(A1, "Alpha", "Beta", null),
                new SlotMeasurementRequest(A1, "Alpha", "Beta", -1)
        );
        //noinspection unchecked
        ArgumentCaptor<Stream<Integer>> commentIdStreamCaptor = ArgumentCaptor.forClass(Stream.class);
        List<Comment> comments = List.of(
                new Comment(1, "Banana", "custard"),
                new Comment(2, "Rhubarb", "custard")
        );
        final String problem = "No such comment";
        when(mockCommentValidationService.validateCommentIds(any(), any()))
                .then(Matchers.addProblem(problem, comments));
        final List<String> problems = new ArrayList<>(1);
        assertSame(comments, service.validateComments(problems, sms));
        verify(mockCommentValidationService).validateCommentIds(any(), commentIdStreamCaptor.capture());
        assertThat(commentIdStreamCaptor.getValue()).containsExactly(1, 2, -1);
    }

    @ParameterizedTest
    @CsvSource({
            "cDNA concentration,10,10,",
            "cDNA concentration,10,10.0,",
            "Library concentration,10,10,",
            "Library concentration,10,10.0,",
            "Cq value,20,20,",
            "Cq value,20,20.0,",
            "Sploop,20,,",
            "Cq value,20,x!,,Bad value",
    })
    public void testSanitiseMeasurementValue(String name, String value, String sanValue, String problem) {
        Sanitiser<String> san, otherSan;
        if (name.equals("cDNA concentration") || name.equals("Library concentration")) {
            san = mockConcSan;
            otherSan = mockCqSan;
        } else if (name.equals("Cq value")) {
            san = mockCqSan;
            otherSan = mockConcSan;
        } else {
            san = null;
            otherSan = null;
        }
        if (san!=null) {
            if (problem!=null) {
                when(san.sanitise(any(), any())).then(invocation -> {
                    Collection<String> problems = invocation.getArgument(0);
                    problems.add(problem);
                    return sanValue;
                });
            } else if (sanValue!=null) {
                when(san.sanitise(any(), any())).thenReturn(sanValue);
            }
        }
        final List<String> problems = new ArrayList<>(problem==null ? 0 : 1);
        assertEquals(sanValue, service.sanitiseMeasurementValue(problems, name, value));
        if (san==null) {
            verifyNoInteractions(mockConcSan);
            verifyNoInteractions(mockCqSan);
        } else {
            verify(san).sanitise(problems, value);
            verifyNoInteractions(otherSan);
        }
        if (problem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(problem);
        }
    }

    @ParameterizedTest
    @MethodSource("checkForDupeMeasurementsArgs")
    public void testCheckForDupeMeasurements(Collection<SlotMeasurementRequest> smrs, String expectedProblem) {
        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.checkForDupeMeasurements(problems, smrs);
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    static Stream<Arguments> checkForDupeMeasurementsArgs() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3);
        return Arrays.stream(new Object[][] {
                {List.of(),null},
                {List.of(new SlotMeasurementRequest(A1, "Alpha", "10", null)), null},

                {List.of(new SlotMeasurementRequest(A1, "Alpha", "10", null),
                        new SlotMeasurementRequest(A1, "Beta", "10", null),
                        new SlotMeasurementRequest(A2, "Alpha", "10", null),
                        new SlotMeasurementRequest(A2, "Beta", "10", null)), null},

                {List.of(new SlotMeasurementRequest(A1, "Alpha", "10", null),
                        new SlotMeasurementRequest(A1, "Alpha", "20", null)),
                "Measurements specified multiple times: Alpha in A1"},

                {List.of(new SlotMeasurementRequest(A1, "Alpha", "10", null),
                        new SlotMeasurementRequest(A1, "Beta", "20", null),
                        new SlotMeasurementRequest(A1, "Alpha", "30", null),
                        new SlotMeasurementRequest(A2, "Gamma", "40", null),
                        new SlotMeasurementRequest(A2, "Gamma", "50", null),
                        new SlotMeasurementRequest(A3, "Gamma", "60", null)),
                "Measurements specified multiple times: Alpha in A1; Gamma in A2"},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testExecute(boolean withWork) {
        Work work = (withWork ? new Work(100, "SGP100", null, null, null, null, Work.Status.active) : null);
        OperationType opType = EntityFactory.makeOperationType(OP_CDNA_AMP, null, OperationTypeFlag.IN_PLACE);
        User user = EntityFactory.getUser();
        Operation op = new Operation(2, opType, null, null, user);
        Labware lw = EntityFactory.getTube();
        List<SlotMeasurementRequest> smrs = List.of(new SlotMeasurementRequest(new Address(1,1), "Alpha", "10", null));

        when(mockOpService.createOperationInPlace(opType, user, lw, null, null)).thenReturn(op);
        doReturn(List.of()).when(service).createMeasurements(any(), any(), any());
        List<Comment> comments = List.of(new Comment(50, "Banana", "custard"));
        doReturn(comments).when(service).validateComments(any(), any());

        OperationResult opres = service.execute(user, lw, opType, work, comments, smrs);
        assertThat(opres.getLabware()).containsExactly(lw);
        assertThat(opres.getOperations()).containsExactly(op);
        verify(service).createMeasurements(op.getId(), lw, smrs);
        if (work!=null) {
            verify(mockWorkService).link(work, opres.getOperations());
        } else {
            verifyNoInteractions(mockWorkService);
        }
        verify(service).recordComments(op.getId(), comments, lw, smrs);
    }

    @Test
    public void testCreateMeasurements_empty() {
        assertThat(service.createMeasurements(10, EntityFactory.getTube(), List.of())).isEmpty();
        verifyNoInteractions(mockMeasRepo);
    }

    @Test
    public void testCreateMeasurements() {
        Integer opId = 7;
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, 2, sam1.getTissue(), sam1.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.getSlot(A1).getSamples().add(sam1);
        lw.getSlot(A2).getSamples().addAll(List.of(sam1, sam2));
        List<SlotMeasurementRequest> smrs = List.of(
                new SlotMeasurementRequest(A1, "Alpha", "10", null),
                new SlotMeasurementRequest(A1, "Beta", "20", null),
                new SlotMeasurementRequest(A2, "Alpha", "30", null)
        );

        when(mockMeasRepo.saveAll(any())).then(invocation -> {
            Iterable<Measurement> ms = invocation.getArgument(0);
            int mid = 10;
            for (Measurement m : ms) {
                m.setId(mid);
                ++mid;
            }
            return ms;
        });

        Collection<Measurement> ms = (Collection<Measurement>) service.createMeasurements(opId, lw, smrs);

        final Integer slot1Id = lw.getSlot(A1).getId();
        final Integer slot2Id = lw.getSlot(A2).getId();
        assertThat(ms).containsExactly(
                new Measurement(10, "Alpha", "10", sam1.getId(), opId, slot1Id),
                new Measurement(11, "Beta", "20", sam1.getId(), opId, slot1Id),
                new Measurement(12, "Alpha", "30", sam1.getId(), opId, slot2Id),
                new Measurement(13, "Alpha", "30", sam2.getId(), opId, slot2Id)
        );
        verify(mockMeasRepo).saveAll(ms);
    }

    @Test
    public void testRecordComments() {
        Integer opId = 300;
        List<Comment> comments = List.of(
                new Comment(1, "Banana", "custard"),
                new Comment(2, "Rhubarb", "custard")
        );
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId(), null, sam1.getTissue(), sam1.getBioState());
        Labware lw = EntityFactory.makeLabware(lt, sam1, sam2);
        lw.getFirstSlot().addSample(sam2);
        Address A1 = new Address(1,1);
        Address A2 = new Address(1,2);
        List<SlotMeasurementRequest> sms = List.of(
                new SlotMeasurementRequest(A1, "A", "B", 1),
                new SlotMeasurementRequest(A2, "A", "B", 2)
        );

        when(mockOpComRepo.saveAll(any())).then(Matchers.returnArgument());

        service.recordComments(opId, comments, lw, sms);

        verify(mockOpComRepo).saveAll(List.of(
                new OperationComment(null, comments.get(0), opId, sam1.getId(), lw.getSlot(A1).getId(), null),
                new OperationComment(null, comments.get(0), opId, sam2.getId(), lw.getSlot(A1).getId(), null),
                new OperationComment(null, comments.get(1), opId, sam2.getId(), lw.getSlot(A2).getId(), null)
        ));
    }

}