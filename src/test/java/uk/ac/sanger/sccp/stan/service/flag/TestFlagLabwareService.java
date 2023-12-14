package uk.ac.sanger.sccp.stan.service.flag;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FlagLabwareRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.OperationService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertProblem;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;

/**
 * Tests {@link FlagLabwareServiceImp}
 */
class TestFlagLabwareService {
    @Mock
    private OperationService mockOpService;

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
        verify(service, never()).create(any(), any(), any(), any());
    }

    @Test
    void testRecord_valid() {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
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

        OperationResult opres = new OperationResult(List.of(new Operation()), List.of(lw));
        doReturn(opres).when(service).create(any(), any(), any(), any());

        assertSame(opres, service.record(user, new FlagLabwareRequest(lw.getBarcode(), desc)));

        verify(service).loadLabware(any(), eq(lw.getBarcode()));
        verify(service).checkDescription(any(), eq(desc));
        verify(service).loadOpType(any());
        verify(service).create(user, opType, lw, "Alpha beta gamma.");
    }

    @Test
    void testRecord_invalid() {
        when(mockLwRepo.findByBarcode(any())).thenReturn(Optional.empty());
        when(mockOpTypeRepo.findByName(any())).thenReturn(Optional.empty());

        assertValidationException(() -> service.record(null, new FlagLabwareRequest("STAN-404", null)),
                List.of("No user specified.", "Unknown labware barcode: \"STAN-404\"", "Missing flag description.",
                        "Flag labware operation type is missing."));

        verify(service).loadLabware(any(), eq("STAN-404"));
        verify(service).checkDescription(any(), isNull());
        verify(service).loadOpType(any());
        verify(service, never()).create(any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"STAN-1, true,",
            "STAN-404, false, Unknown labware barcode: \"STAN-404\"",
            ", false, No labware barcode supplied.",
    })
    void testLoadLabware(String barcode, boolean exists, String expectedProblem) {
        Labware lw = exists ? EntityFactory.getTube() : null;
        if (barcode!=null && !barcode.isEmpty()) {
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

    @Test
    void testCreate() {
        Labware lw = EntityFactory.getTube();
        User user = EntityFactory.getUser();
        String desc = "Alpha beta";
        OperationType opType = EntityFactory.makeOperationType("Flag labware", null, OperationTypeFlag.IN_PLACE);

        Operation op = new Operation();
        op.setId(500);
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);

        assertEquals(new OperationResult(List.of(op), List.of(lw)), service.create(user, opType, lw, desc));

        verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        verify(mockFlagRepo).save(new LabwareFlag(null, lw, desc, user, op.getId()));
    }
}