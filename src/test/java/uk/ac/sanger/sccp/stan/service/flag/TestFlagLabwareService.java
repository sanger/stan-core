package uk.ac.sanger.sccp.stan.service.flag;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.LabwareFlag.Priority;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FlagLabwareRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.OperationService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.Zip;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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
        verify(service, never()).create(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testRecord_valid(boolean hasWork) {
        User user = EntityFactory.getUser();
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), lw1.getFirstSlot().getSamples().getFirst());
        List<Labware> labware = List.of(lw1, lw2);
        List<String> barcodes = labware.stream().map(Labware::getBarcode).toList();
        Work work = hasWork ? EntityFactory.makeWork("SGP11") : null;
        String workNumber = work==null ? null : work.getWorkNumber();
        String desc = "  Alpha beta   gamma.  ";
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(labware);
        int[] flagIdCounter = {500};
        when(mockFlagRepo.save(any())).then(invocation -> {
            int flagId = flagIdCounter[0]++;
            LabwareFlag flag = invocation.getArgument(0);
            flag.setId(flagId);
            return flag;
        });
        OperationType opType = EntityFactory.makeOperationType("Flag labware", null, OperationTypeFlag.IN_PLACE);

        when(mockOpTypeRepo.findByName(opType.getName())).thenReturn(Optional.of(opType));
        if (hasWork) {
            when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        }

        OperationResult opres = new OperationResult(List.of(new Operation()), labware);
        doReturn(opres).when(service).create(any(), any(), any(), any(), any(), any());

        assertSame(opres, service.record(user, new FlagLabwareRequest(barcodes, desc, workNumber, Priority.note)));

        verify(service).loadLabware(any(), eq(barcodes));
        verify(service).checkDescription(any(), eq(desc));
        verify(service).loadOpType(any());
        if (hasWork) {
            verify(mockWorkService).validateUsableWork(any(), eq(work.getWorkNumber()));
        } else {
            verifyNoInteractions(mockWorkService);
        }
        verify(service).create(user, opType, labware, "Alpha beta gamma.", work, Priority.note);
    }

    @Test
    void testRecord_invalid() {
        Labware lw = EntityFactory.getTube();
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(List.of(lw));
        when(mockOpTypeRepo.findByName(any())).thenReturn(Optional.empty());
        List<String> barcodes = List.of(lw.getBarcode().toLowerCase(), "STAN-404");
        assertValidationException(() -> service.record(null, new FlagLabwareRequest(barcodes,
                        null, null, null)),
                List.of("No user specified.", "Unknown labware barcode: [\"STAN-404\"]", "Missing flag description.",
                        "Flag labware operation type is missing.", "No priority specified."));

        verify(service).loadLabware(any(), eq(barcodes));
        verify(service).checkDescription(any(), isNull());
        verify(service).loadOpType(any());
        verify(service, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testRecord_badWork() {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        List<String> barcodes = List.of(lw.getBarcode());
        FlagLabwareRequest request = new FlagLabwareRequest(barcodes, "flag desc", "SGP4", Priority.flag);
        when(mockLwRepo.findByBarcodeIn(barcodes)).thenReturn(List.of(lw));
        when(mockWorkService.validateUsableWork(any(), any())).then(addProblem("Bad work"));
        OperationType opType = EntityFactory.makeOperationType("Flag labware", null);
        when(mockOpTypeRepo.findByName(opType.getName())).thenReturn(Optional.of(opType));

        assertValidationException(() -> service.record(user, request), List.of("Bad work"));
        verify(service).loadLabware(any(), eq(barcodes));
        verify(service).checkDescription(any(), eq("flag desc"));
        verify(service).loadOpType(any());
        verify(mockWorkService).validateUsableWork(any(), eq("SGP4"));
        verify(service, never()).create(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans={true,false})
    void testLoadLabware_missingBarcode(boolean bcNull) {
        List<String> barcodes = Arrays.asList("STAN-1", bcNull ? null : "");
        List<String> problems = new ArrayList<>(1);
        assertNull(service.loadLabware(problems, barcodes));
        assertProblem(problems, "Barcodes array has missing elements.");
    }

    @ParameterizedTest
    @CsvSource({"Stan-1, true,",
            "STAN-1, false, Labware is empty: [STAN-1]",
            "Stan-1/STAN-404, true, Unknown labware barcode: [\"STAN-404\"]",
            ", false, No labware barcodes supplied.",
    })
    void testLoadLabware(String barcodesJoined, boolean containsSample, String expectedProblem) {
        List<String> barcodes = (barcodesJoined == null ? null : Arrays.asList(barcodesJoined.split("/")));

        String validBarcode = barcodes == null ? null
                : barcodes.stream().filter(bc -> !bc.equalsIgnoreCase("STAN-404")).findAny().orElse(null);
        List<Labware> lwList;
        if (validBarcode!=null) {
            Labware lw;
            if (containsSample) {
                lw = EntityFactory.makeLabware(EntityFactory.getTubeType(), EntityFactory.getSample());
            } else {
                lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
            }
            lw.setBarcode(validBarcode.toUpperCase());
            when(mockLwRepo.findByBarcodeIn(barcodes)).thenReturn(List.of(lw));
            lwList = List.of(lw);
        } else {
            when(mockLwRepo.findByBarcodeIn(any())).thenReturn(List.of());
            lwList = null;
        }

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertEquals(lwList, service.loadLabware(problems, barcodes));
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
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), lw1.getFirstSlot().getSamples().getFirst());
        List<Labware> labware = List.of(lw1, lw2);
        User user = EntityFactory.getUser();
        Work work = (hasWork ? EntityFactory.makeWork("SGP1") : null);
        String desc = "Alpha beta";
        Priority priority = Priority.flag;
        OperationType opType = EntityFactory.makeOperationType("Flag labware", null, OperationTypeFlag.IN_PLACE);

        final int[] opIdCounter = {500};
        final List<Operation> returnedOps = new ArrayList<>(labware.size());
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).then(invocation -> {
            Operation op = new Operation();
            op.setId(++opIdCounter[0]);
            returnedOps.add(op);
            return op;
        });
        final OperationResult result = service.create(user, opType, labware, desc, work, priority);

        for (Labware lw : labware) {
            verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        }

        assertThat(returnedOps).hasSameSizeAs(labware);
        assertEquals(new OperationResult(returnedOps, labware), result);

        List<LabwareFlag> expectedFlags = Zip.map(labware.stream(), returnedOps.stream(),
                        (lw, op) -> new LabwareFlag(null, lw, desc, user, op.getId(), priority))
                .toList();
        verify(mockFlagRepo).saveAll(expectedFlags);
        if (hasWork) {
            verify(mockWorkService).link(work, returnedOps);
        } else {
            verifyNoInteractions(mockWorkService);
        }
    }
}