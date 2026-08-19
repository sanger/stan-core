package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.stan.service.LibraryConServiceImp.RequestData;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;

/** {@link LibraryConServiceImp} */
class TestLibraryConService {
    @Mock
    ReagentTransferService mockReagentTransferService;
    @Mock
    OpWithSlotMeasurementsService mockOpWithSlotMeasurementsService;
    @Mock
    LibraryConValidationService mockValService;

    @InjectMocks
    LibraryConServiceImp service;

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

    @ParameterizedTest
    @ValueSource(ints={0,1,2,3})
    void testPerform(int test) {
        boolean valid = (test==0);
        boolean missingUser = (test==1);
        boolean missingRequest = (test==2);
        boolean failValidation = (test==3);
        User user = (missingUser ? null : EntityFactory.getUser());
        LibraryConRequest request = (missingRequest ? null : new LibraryConRequest());
        String valError = (failValidation ? "Bad request" : missingUser ? "No user supplied." :
                                                            missingRequest ? "No request supplied." : null);
        if (failValidation) {
            doAnswer(invocation -> {
                RequestData data = invocation.getArgument(0);
                data.problems.add(valError);
                return null;
            }).when(mockValService).validate(any());
        }

        if (valid) {
            OperationResult opres = new OperationResult(List.of(), List.of());
            doReturn(opres).when(service).record(any());
            assertSame(opres, service.perform(user, request));
            ArgumentCaptor<RequestData> dataCaptor = ArgumentCaptor.forClass(RequestData.class);
            verify(service).record(dataCaptor.capture());
            RequestData data = dataCaptor.getValue();
            assertSame(request, data.request);
            assertSame(user, data.user);
            assertThat(data.problems).isNotNull().isEmpty();
        } else {
            assertValidationException(() -> service.perform(user, request), "The request could not be validated.", valError);
            verify(service, never()).record(any());
        }
    }

    @Test
    void testRecord() {
        final Address A1 = new Address(1, 1);
        User user = EntityFactory.getUser();
        LibraryConRequest request = new LibraryConRequest();
        request.setReagentTransfers(List.of(new ReagentTransfer("RP1", A1, A1)));
        RequestData data = new RequestData(request, user, null);
        data.reagentOpType = EntityFactory.makeOperationType("Dual index plate", null);
        data.ampOpType = EntityFactory.makeOperationType("Amplification", null);
        data.work = EntityFactory.makeWork("SGP1");
        Labware lw = EntityFactory.getTube();
        data.labware = lw;
        data.comments = List.of(new Comment(1, "Bananas", "Bananas"));
        data.sanitisedMeasurements = List.of(new SlotMeasurementRequest(A1, "NAME", "VALUE", List.of(1)));
        // data.request.getReagentTransfers(), data.reagentPlates, data.labware, data.reagentPlateType
        data.reagentPlates = UCMap.from(ReagentPlate::getBarcode, new ReagentPlate("RP1", "rt1"));
        data.reagentPlateType = "rt2";
        Operation op1 = new Operation();
        op1.setId(1);
        OperationResult opres1 = new OperationResult(List.of(op1), List.of(lw));
        Operation op2 = new Operation();
        op2.setId(2);
        OperationResult opres2 = new OperationResult(List.of(op2), List.of(lw));

        when(mockReagentTransferService.record(any(), any(), any(), any(), any(), any(), any())).thenReturn(opres1);
        when(mockOpWithSlotMeasurementsService.execute(any(), any(), any(), any(), any(), any())).thenReturn(opres2);

        OperationResult result = service.record(data);

        verify(mockReagentTransferService).record(user, data.reagentOpType, data.work, request.getReagentTransfers(),
                data.reagentPlates, lw, data.reagentPlateType);
        verify(mockOpWithSlotMeasurementsService).execute(user, lw, data.ampOpType, data.work, data.comments,
                data.sanitisedMeasurements);

        assertThat(result.getLabware()).containsExactly(lw);
        assertThat(result.getOperations()).containsExactly(op1, op2);
    }
}