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
import uk.ac.sanger.sccp.stan.service.LibraryConServiceImp.LibConData;
import uk.ac.sanger.sccp.stan.service.LibraryConServiceImp.RequestData;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;

/** Test {@link LibraryConServiceImp} */
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
                LibConData libConData = invocation.getArgument(0);
                libConData.problems.add(valError);
                return null;
            }).when(mockValService).validate(any());
        }

        if (valid) {
            Labware lw = EntityFactory.getTube();
            Operation op = new Operation();
            op.setId(100);
            doAnswer(invocation -> {
                List<Operation> ops = invocation.getArgument(0);
                List<Labware> lws = invocation.getArgument(1);
                ops.add(op);
                lws.add(lw);
                return null;
            }).when(service).record(any(), any(), any(), any());
            OperationResult opres = new OperationResult(List.of(op), List.of(lw));
            assertEquals(opres, service.perform(user, List.of(request)));
            ArgumentCaptor<LibConData> libConDataCaptor = ArgumentCaptor.forClass(LibConData.class);
            ArgumentCaptor<RequestData> dataCaptor = ArgumentCaptor.forClass(RequestData.class);
            verify(service).record(any(), any(), libConDataCaptor.capture(), dataCaptor.capture());
            LibConData libConData = libConDataCaptor.getValue();
            RequestData data = dataCaptor.getValue();
            assertSame(request, data.request);
            assertSame(user, libConData.user);
            assertThat(libConData.problems).isNotNull().isEmpty();
        } else {
            List<LibraryConRequest> requests = (request==null ? List.of() : List.of(request));
            assertValidationException(() -> service.perform(user, requests), "The request could not be validated.", valError);
            verify(service, never()).record(any(), any(), any(), any());
        }
    }

    @Test
    void testRecord() {
        final Address A1 = new Address(1, 1);
        User user = EntityFactory.getUser();
        LibraryConRequest request = new LibraryConRequest();
        LibConData libConData = new LibConData(null, user, List.of(request));
        request.setReagentTransfers(List.of(new ReagentTransfer("RP1", A1, A1)));
        RequestData data = libConData.data.getFirst();
        libConData.reagentOpType = EntityFactory.makeOperationType("Dual index plate", null);
        libConData.ampOpType = EntityFactory.makeOperationType("Amplification", null);
        data.work = EntityFactory.makeWork("SGP1");
        Labware lw = EntityFactory.getTube();
        data.labware = lw;
        libConData.comments = List.of(new Comment(1, "Bananas", "Bananas"));
        data.sanitisedMeasurements = List.of(new SlotMeasurementRequest(A1, "NAME", "VALUE", List.of(1)));
        // data.request.getReagentTransfers(), data.reagentPlates, data.labware, data.reagentPlateType
        libConData.reagentPlates = UCMap.from(ReagentPlate::getBarcode, new ReagentPlate("RP1", "rt1"));
        data.reagentPlateType = "rt2";
        Operation op1 = new Operation();
        op1.setId(1);
        OperationResult opres1 = new OperationResult(List.of(op1), List.of(lw));
        Operation op2 = new Operation();
        op2.setId(2);
        OperationResult opres2 = new OperationResult(List.of(op2), List.of(lw));

        when(mockReagentTransferService.record(any(), any(), any(), any(), any(), any(), any())).thenReturn(opres1);
        when(mockOpWithSlotMeasurementsService.execute(any(), any(), any(), any(), any(), any())).thenReturn(opres2);

        final List<Operation> opList = new ArrayList<>();
        final List<Labware> lwList = new ArrayList<>();
        service.record(opList, lwList, libConData, data);

        verify(mockReagentTransferService).record(user, libConData.reagentOpType, data.work, request.getReagentTransfers(),
                libConData.reagentPlates, lw, data.reagentPlateType);
        verify(mockOpWithSlotMeasurementsService).execute(user, lw, libConData.ampOpType, data.work, libConData.comments,
                data.sanitisedMeasurements);

        assertThat(lwList).containsExactly(lw);
        assertThat(opList).containsExactly(op1, op2);
    }
}