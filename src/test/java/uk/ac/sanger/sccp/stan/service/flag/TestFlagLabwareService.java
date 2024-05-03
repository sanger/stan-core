package uk.ac.sanger.sccp.stan.service.flag;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FlagLabwareRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.OperationService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;

/**
 * Tests {@link FlagLabwareServiceImp}
 */
class TestFlagLabwareService {
    @Mock
    private OperationService mockOpService;
    @Mock
    private WorkService mockWorkService;

    @Mock
    private LabwareFlagRepo mockFlagRepo;
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;

    @InjectMocks
    private FlagLabwareServiceImp service;

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
    void testRecord_noRequest() {
        User user = EntityFactory.getUser();
        assertValidationException(() -> service.record(user, null),
                List.of("No request supplied."));
        verify(service, never()).create(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testRecord_valid(boolean hasWork) {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        Work work = hasWork ? EntityFactory.makeWork("SGP11") : null;
        String workNumber = work==null ? null : work.getWorkNumber();
        String desc = "  Alpha beta   gamma.  ";
        when(mockLwRepo.findByBarcode(lw.getBarcode())).thenReturn(Optional.of(lw));
        final Integer flagId = 500;
        when(mockFlagRepo.save(any())).then(invocation -> {
            LabwareFlag flag = invocation.getArgument(0);
            flag.setId(flagId);
            return flag;
        });
        OperationType opType = EntityFactory.makeOperationType("Flag labware", null, OperationTypeFlag.IN_PLACE);

        when(mockOpTypeRepo.findByName(opType.getName())).thenReturn(Optional.of(opType));
        if (hasWork) {
            when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        }

        OperationResult opres = new OperationResult(List.of(new Operation()), List.of(lw));
        doReturn(opres).when(service).create(any(), any(), any(), any(), any());

        assertSame(opres, service.record(user, new FlagLabwareRequest(lw.getBarcode(), desc, workNumber)));

        verify(service).loadLabware(any(), eq(lw.getBarcode()));
        verify(service).checkDescription(any(), eq(desc));
        verify(service).loadOpType(any());
        if (hasWork) {
            verify(mockWorkService).validateUsableWork(any(), eq(work.getWorkNumber()));
        } else {
            verifyNoInteractions(mockWorkService);
        }
        verify(service).create(user, opType, lw, "Alpha beta gamma.", work);
    }

    @Test
    void testRecord_invalid() {
        when(mockLwRepo.findByBarcode(any())).thenReturn(Optional.empty());
        when(mockOpTypeRepo.findByName(any())).thenReturn(Optional.empty());

        assertValidationException(() -> service.record(null, new FlagLabwareRequest("STAN-404", null, null)),
                List.of("No user specified.", "Unknown labware barcode: \"STAN-404\"", "Missing flag description.",
                        "Flag labware operation type is missing."));

        verify(service).loadLabware(any(), eq("STAN-404"));
        verify(service).checkDescription(any(), isNull());
        verify(service).loadOpType(any());
        verify(service, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void testRecord_badWork() {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        FlagLabwareRequest request = new FlagLabwareRequest(lw.getBarcode(), "flag desc", "SGP4");
        when(mockLwRepo.findByBarcode(lw.getBarcode())).thenReturn(Optional.of(lw));
        when(mockWorkService.validateUsableWork(any(), any())).then(addProblem("Bad work"));
        OperationType opType = EntityFactory.makeOperationType("Flag labware", null);
        when(mockOpTypeRepo.findByName(opType.getName())).thenReturn(Optional.of(opType));

        assertValidationException(() -> service.record(user, request), List.of("Bad work"));
        verify(service).loadLabware(any(), eq(lw.getBarcode()));
        verify(service).checkDescription(any(), eq("flag desc"));
        verify(service).loadOpType(any());
        verify(mockWorkService).validateUsableWork(any(), eq("SGP4"));
        verify(service, never()).create(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"STAN-1, true, true,",
            "STAN-1, true, false, Labware STAN-1 is empty.",
            "STAN-404, false, false, Unknown labware barcode: \"STAN-404\"",
            ", false, false, No labware barcode supplied.",
    })
    void testLoadLabware(String barcode, boolean exists, boolean containsSample, String expectedProblem) {
        Labware lw;
        if (!exists) {
            lw = null;
        } else if (containsSample) {
            lw = EntityFactory.makeLabware(EntityFactory.getTubeType(), EntityFactory.getSample());
        } else {
            lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        }
        if (barcode!=null && !barcode.isEmpty()) {
            if (lw!=null) {
                lw.setBarcode(barcode);
            }
            when(mockLwRepo.findByBarcode(barcode)).thenReturn(Optional.ofNullable(lw));
        }

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(lw, service.loadLabware(problems, barcode));
        assertProblem(problems, expectedProblem);
    }

    @ParameterizedTest
    @CsvSource({
            ", , Missing flag description.",
            "'',,Missing flag description.",
            "'   alpha  beta ', alpha beta,",
            "long,,Description too long.",
            "max,,,",
    })
    void testCheckDescription(String input, String expectedOutput, String expectedProblem) {
        int maxLen = FlagLabwareServiceImp.MAX_DESCRIPTION_LEN;
        if ("long".equals(input)) {
            input = "X".repeat(maxLen+1);
            expectedOutput = input;
        } else if ("max".equals(input)) {
            input = "X".repeat(maxLen);
            expectedOutput = input;
        }
        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertEquals(expectedOutput, service.checkDescription(problems, input));
        assertProblem(problems, expectedProblem);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testCreate(boolean hasWork) {
        Labware lw = EntityFactory.getTube();
        User user = EntityFactory.getUser();
        Work work = (hasWork ? EntityFactory.makeWork("SGP1") : null);
        String desc = "Alpha beta";
        OperationType opType = EntityFactory.makeOperationType("Flag labware", null, OperationTypeFlag.IN_PLACE);

        Operation op = new Operation();
        op.setId(500);
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);

        assertEquals(new OperationResult(List.of(op), List.of(lw)), service.create(user, opType, lw, desc, work));

        verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        verify(mockFlagRepo).save(new LabwareFlag(null, lw, desc, user, op.getId()));
        if (hasWork) {
            verify(mockWorkService).link(work, List.of(op));
        } else {
            verifyNoInteractions(mockWorkService);
        }
    }
}