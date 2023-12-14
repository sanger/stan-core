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
import uk.ac.sanger.sccp.stan.request.OrientationRequest;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;

class TestOrientationService {
    @Mock
    private LabwareValidatorFactory mockLwValFactory;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private OperationService mockOpService;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private LabwareNoteRepo mockLwNoteRepo;

    @InjectMocks
    private OrientationServiceImp service;

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
    void testPerform_noRequest() {
        assertValidationException(() -> service.perform(EntityFactory.getUser(), null),
                List.of("No request supplied."));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testPerform_valid(boolean orientationCorrect) {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Orientation QC", null, OperationTypeFlag.IN_PLACE);
        Labware lw = EntityFactory.getTube();
        Work work = EntityFactory.makeWork("SGP1");
        OrientationRequest request = new OrientationRequest(lw.getBarcode(), work.getWorkNumber(), orientationCorrect);

        doReturn(lw).when(service).checkLabware(any(), any());
        doReturn(work).when(mockWorkService).validateUsableWork(any(), any());
        doReturn(opType).when(service).loadOpType(any());

        OperationResult opres = new OperationResult(List.of(new Operation()), List.of(lw));
        doReturn(opres).when(service).record(any(), any(), any(), any(), anyBoolean());

        assertSame(opres, service.perform(user, request));

        verify(service).checkLabware(any(), eq(lw.getBarcode()));
        verify(service).loadOpType(any());
        verify(mockWorkService).validateUsableWork(any(), eq(work.getWorkNumber()));
        verify(service).record(user, opType, lw, work, orientationCorrect);
    }

    @Test
    void testPerform_invalid() {
        doAnswer(addProblem("Bad lw", null)).when(service).checkLabware(any(), any());
        doAnswer(addProblem("Bad work", null)).when(mockWorkService).validateUsableWork(any(), any());
        doAnswer(addProblem("Bad optype", null)).when(service).loadOpType(any());

        OrientationRequest request = new OrientationRequest("STAN-1", "SGP1", true);

        assertValidationException(() -> service.perform(null, request),
                List.of("No user supplied.", "Bad lw", "Bad work", "Bad optype"));

        verify(service).checkLabware(any(), eq(request.getBarcode()));
        verify(service).loadOpType(any());
        verify(mockWorkService).validateUsableWork(any(), eq(request.getWorkNumber()));
        verify(service, never()).record(any(), any(), any(), any(), anyBoolean());
    }

    @ParameterizedTest
    @CsvSource({"false,true", "true,false", "true,true"})
    void testCheckLabware(boolean anyProblem, boolean lwExists) {
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        Labware lw = lwExists ? EntityFactory.getTube() : null;
        List<Labware> lws = lwExists ? List.of(lw) : List.of();
        when(val.loadLabware(any(), any())).thenReturn(lws);
        when(val.getLabware()).thenReturn(lws);
        String problem = anyProblem ? "Bad lw" : null;
        List<String> errors = anyProblem ? List.of(problem) : List.of();
        when(val.getErrors()).thenReturn(errors);
        final List<String> problems = new ArrayList<>(anyProblem ? 1 : 0);
        assertSame(lw, service.checkLabware(problems, "STAN-1"));

        verify(val).setSingleSample(true);
        verify(val).setBlockRequired(true);
        verify(val).loadLabware(mockLwRepo, List.of("STAN-1"));

        assertProblem(problems, problem);
    }

    @Test
    void testCheckLabware_noBarcode() {
        final List<String> problems = new ArrayList<>(1);
        assertNull(service.checkLabware(problems, null));
        verifyNoInteractions(mockLwValFactory);
        assertProblem(problems, "No barcode supplied.");
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLoadOpType(boolean exists) {
        OperationType opType = exists ? EntityFactory.makeOperationType("Orientation QC", null, OperationTypeFlag.IN_PLACE) : null;
        when(mockOpTypeRepo.findByName("Orientation QC")).thenReturn(Optional.ofNullable(opType));
        final List<String> problems = new ArrayList<>(exists ? 0 : 1);
        assertSame(opType, service.loadOpType(problems));
        assertProblem(problems, exists ? null : "Operation type not available: Orientation QC.");
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testRecord(boolean orientationCorrect) {
        OperationType opType = EntityFactory.makeOperationType("Orientation QC", null, OperationTypeFlag.IN_PLACE);
        Labware lw = EntityFactory.getTube();
        Work work = EntityFactory.makeWork("SGP1");
        Operation op = new Operation();
        op.setId(100);
        doReturn(op).when(mockOpService).createOperationInPlace(any(), any(), any(), any(), any());
        User user = EntityFactory.getUser();

        assertEquals(new OperationResult(List.of(op), List.of(lw)), service.record(user, opType, lw, work, orientationCorrect));

        verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        verify(mockWorkService).link(work, List.of(op));
        verify(mockLwNoteRepo).save(new LabwareNote(null, lw.getId(), op.getId(), "Orientation",
                orientationCorrect ? "correct" : "incorrect"));
    }
}