package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.LibraryPrepServiceImp.RequestData;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;

/**
 * Test {@link LibraryPrepServiceImp}
 */
class TestLibraryPrepService {
    @Mock
    private SlotCopyService mockSlotCopyService;
    @Mock
    private ReagentTransferService mockReagentTransferService;
    @Mock
    private OpWithSlotMeasurementsService mockOpWithSlotMeasurementsService;
    @Mock
    private StoreService mockStoreService;
    @Mock
    private LibraryPrepValidationService mockValService;
    @Mock
    private Transactor mockTransactor;

    @InjectMocks
    private LibraryPrepServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
    }

    @AfterEach
    void teardown() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @CsvSource({
            "true,true,true",
            "false,true,false",
            "true,false,false",
            "true,true,false",
    })
    void testPerform(boolean hasUser, boolean hasRequest, boolean succeeds) {
        mockTransactor(mockTransactor);
        User user = hasUser ? EntityFactory.getUser() : null;
        LibraryPrepRequest request;
        if (hasRequest) {
            request = new LibraryPrepRequest();
            request.setWorkNumber("SGP1");
        } else {
            request = null;
        }
        OperationResult opres = null;
        if (hasRequest) {
            if (succeeds) {
                opres = new OperationResult(List.of(new Operation()), List.of(EntityFactory.getTube()));
                final OperationResult finalOpRes = opres;
                doAnswer(invocation -> {
                    RequestData data = invocation.getArgument(0);
                    data.barcodesToUnstore.add("unstorebarcode");
                    return finalOpRes;
                }).when(service).performInsideTransaction(any());
            } else {
                ValidationException ex = new ValidationException(List.of("Bad"));
                doThrow(ex).when(service).performInsideTransaction(any());
            }
        }

        if (!hasRequest) {
            assertValidationException(() -> service.perform(user, null), List.of("No request supplied."));
            verify(service, never()).performInsideTransaction(any());
            verifyNoInteractions(mockTransactor);
            return;
        }

        if (succeeds) {
            assertSame(opres, service.perform(user, request));
        } else {
            assertValidationException(() -> service.perform(user, request), List.of("Bad"));
        }
        verify(mockTransactor).transact(eq("LibraryPrep"), any());
        ArgumentCaptor<RequestData> dataCaptor = ArgumentCaptor.forClass(RequestData.class);
        verify(service).performInsideTransaction(dataCaptor.capture());
        RequestData data = dataCaptor.getValue();
        assertSame(data.request, request);
        assertSame(data.user, user);
        assertProblem(data.problems, user==null ? "No user supplied." : null);
        if (succeeds) {
            verify(mockStoreService).discardStorage(user, Set.of("unstorebarcode"));
        } else {
            verifyNoInteractions(mockStoreService);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "false,false",
            "true,false",
            "false,true",
            "true,true"
    })
    public void testPerformInsideTransaction(boolean priorProblem, boolean newProblem) {
        RequestData data = new RequestData(new LibraryPrepRequest(), EntityFactory.getUser(), new ArrayList<>());
        if (priorProblem) {
            data.problems.add("Prior problem.");
        }
        if (newProblem) {
            doAnswer(invocation -> {
                RequestData rd = invocation.getArgument(0);
                rd.problems.add("New problem.");
                return null;
            }).when(mockValService).validate(any());
        }
        List<String> expectedProblems = new ArrayList<>(2);
        if (priorProblem) {
            expectedProblems.add("Prior problem.");
        }
        if (newProblem) {
            expectedProblems.add("New problem.");
        }
        OperationResult opres = expectedProblems.isEmpty() ?
                new OperationResult(List.of(new Operation()), List.of(EntityFactory.getTube()))
                : null;
        doReturn(opres).when(service).record(any());
        if (expectedProblems.isEmpty()) {
            assertSame(opres, service.performInsideTransaction(data));
            verify(service).record(data);
        } else {
            assertValidationException(() -> service.performInsideTransaction(data), expectedProblems);
            verify(service, never()).record(any());
        }
    }

    @Test
    void testRecord() {
        Labware destLw = EntityFactory.getTube();
        Operation[] ops = IntStream.rangeClosed(1,3).mapToObj(i -> {
            Operation op = new Operation();
            op.setId(i);
            return op;
        }).toArray(Operation[]::new);
        OperationResult[] opreses = Arrays.stream(ops).map(op -> new OperationResult(List.of(op), List.of(destLw)))
                .toArray(OperationResult[]::new);
        when(mockSlotCopyService.record(any(), any(), any())).thenReturn(opreses[0]);
        when(mockReagentTransferService.record(any(), any(), any(), any(), any(), any(), any())).thenReturn(opreses[1]);
        when(mockOpWithSlotMeasurementsService.execute(any(), any(), any(), any(), any(), any())).thenReturn(opreses[2]);

        LibraryPrepRequest request = new LibraryPrepRequest();
        User user = EntityFactory.getUser();
        RequestData data = new RequestData(request, user, List.of());
        data.slotCopyData = new SlotCopyValidationService.Data(new SlotCopyRequest());
        data.reagentOpType = EntityFactory.makeOperationType("Dual index plate", null);
        data.work = EntityFactory.makeWork("SGP1");
        data.request.setReagentTransfers(List.of(new ReagentTransferRequest.ReagentTransfer()));
        data.reagentPlates = UCMap.from(ReagentPlate::getBarcode, EntityFactory.makeReagentPlate("RP1"));
        data.ampOpType = EntityFactory.makeOperationType("Amplify", null);
        data.comments = List.of(new Comment(1, "com1", "cat1"));
        data.sanitisedMeasurements = List.of(new SlotMeasurementRequest(new Address(1,1), "name", "value", 1));

        OperationResult opres = service.record(data);
        assertThat(opres.getOperations()).containsExactly(ops);
        assertThat(opres.getLabware()).containsExactly(destLw);

        verify(mockSlotCopyService).record(user, data.slotCopyData, data.barcodesToUnstore);
        verify(mockReagentTransferService).record(user, data.reagentOpType, data.work, data.request.getReagentTransfers(),
                data.reagentPlates, data.destination, data.reagentPlateType);
        verify(mockOpWithSlotMeasurementsService).execute(user, destLw, data.ampOpType, data.work, data.comments, data.sanitisedMeasurements);
    }
}