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
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyContent;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyDestination;
import uk.ac.sanger.sccp.stan.service.LibraryPrepServiceImp.RequestData;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.mayAddProblem;

class TestLibraryPrepValidationService {
    @Mock
    private SlotCopyValidationService mockScValService;
    @Mock
    private ReagentTransferValidatorService mockRtValService;
    @Mock
    private ReagentTransferService mockRtService;
    @Mock
    private OpWithSlotMeasurementsService mockOwsmService;

    @InjectMocks
    private LibraryPrepValidationServiceImp service;

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
    void testValidate() {
        RequestData data = new RequestData(new LibraryPrepRequest(), EntityFactory.getUser(), List.of());
        List<BiConsumer<LibraryPrepValidationServiceImp, RequestData>> subFunctions = List.of(
                LibraryPrepValidationServiceImp::scValidate,
                LibraryPrepValidationServiceImp::rtValidate,
                LibraryPrepValidationServiceImp::owsmValidate
        );
        subFunctions.forEach(func -> func.accept(doNothing().when(service), any()));
        service.validate(data);
        subFunctions.forEach(func -> func.accept(verify(service), data));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testScValidate(boolean existingDest) {
        Work work = EntityFactory.makeWork("SGP1");
        Labware lw = (existingDest ? EntityFactory.getTube() : null);
        LabwareType lt = EntityFactory.getTubeType();
        when(mockScValService.validateRequest(any(), any())).then(invocation -> {
            SlotCopyRequest scRequest = invocation.getArgument(1);
            SlotCopyValidationService.Data scData = new SlotCopyValidationService.Data(scRequest);
            if (existingDest) {
                scData.destLabware = UCMap.from(Labware::getBarcode, lw);
            } else {
                scData.lwTypes = UCMap.from(LabwareType::getName, lt);
            }
            scData.work = work;
            scData.problems.add("sc problem");
            return scData;
        });

        LibraryPrepRequest request = new LibraryPrepRequest();
        request.setWorkNumber("SGP1");
        request.setSources(List.of(new SlotCopyRequest.SlotCopySource("STAN-1", Labware.State.active)));
        request.setDestination(new SlotCopyDestination());
        if (existingDest) {
            request.getDestination().setBarcode("STAN-1");
        } else {
            request.getDestination().setLabwareType(lt.getName());
        }
        final User user = EntityFactory.getUser();
        RequestData data = new RequestData(request, user, new ArrayList<>());

        service.scValidate(data);

        SlotCopyRequest expectedScRequest = new SlotCopyRequest("Transfer", "SGP1",
                null, request.getSources(), List.of(request.getDestination()));
        verify(mockScValService).validateRequest(user, expectedScRequest);

        if (existingDest) {
            assertSame(data.destination, lw);
            assertSame(data.destLabwareType, lw.getLabwareType());
        } else {
            assertNull(data.destination);
            assertSame(data.destLabwareType, lt);
        }
        assertSame(data.work, work);
        assertThat(data.problems).containsExactly("sc problem");
        assertNotNull(data.slotCopyData);
    }

    @Test
    void testRtValidate() {
        LibraryPrepRequest request = new LibraryPrepRequest();
        User user = EntityFactory.getUser();
        RequestData data = new RequestData(request, user, new ArrayList<>());
        data.destLabwareType = EntityFactory.getTubeType();
        request.setReagentTransfers(List.of(new ReagentTransfer("RPNC", null, null)));
        request.setReagentPlateType("reagent plate type");
        OperationType opType = EntityFactory.makeOperationType("Dual index plate", null);
        UCMap<ReagentPlate> reagentPlates = UCMap.from(ReagentPlate::getBarcode, EntityFactory.makeReagentPlate("RPBC"));
        String reagentPlateType = "sanitised reagent plate type";

        when(mockRtService.loadOpType(any(), any())).thenReturn(opType);
        when(mockRtService.loadReagentPlates(any())).thenReturn(reagentPlates);
        when(mockRtService.checkPlateType(any(), any(), any())).thenReturn(reagentPlateType);
        mayAddProblem("transfer problem").when(mockRtValService).validateTransfers(any(), any(), any(), any());

        service.rtValidate(data);
        verify(mockRtService).loadOpType(data.problems, "Dual index plate");
        verify(mockRtService).loadReagentPlates(request.getReagentTransfers());
        verify(mockRtService).checkPlateType(data.problems, reagentPlates.values(), request.getReagentPlateType());
        verify(mockRtValService).validateTransfers(data.problems, request.getReagentTransfers(), reagentPlates, data.destLabwareType);

        assertSame(data.reagentOpType, opType);
        assertSame(data.reagentPlates, reagentPlates);
        assertSame(data.reagentPlateType, reagentPlateType);
    }

    @Test
    void testOwsmValidate_noLabwareType() {
        RequestData data = new RequestData(new LibraryPrepRequest(), EntityFactory.getUser(), new ArrayList<>());
        service.owsmValidate(data);
        verifyNoInteractions(mockOwsmService);
    }

    @Test
    public void testOwsmValidate() {
        LibraryPrepRequest request = new LibraryPrepRequest();
        final SlotCopyDestination scd = new SlotCopyDestination();
        final Address A1 = new Address(1, 1), A2 = new Address(1, 2);
        scd.setContents(List.of(
                new SlotCopyContent("STAN-1", A1, A1),
                new SlotCopyContent("STAN-2", A1, A2),
                new SlotCopyContent("STAN-3", A1, null)
        ));
        request.setDestination(scd);
        request.setSlotMeasurements(List.of(new SlotMeasurementRequest()));

        User user = EntityFactory.getUser();
        RequestData data = new RequestData(request, user, new ArrayList<>());
        data.destLabwareType = EntityFactory.getTubeType();

        OperationType opType = EntityFactory.makeOperationType("Amplification", null);
        List<Comment> validatedComments = List.of(new Comment(1, "Bananas", "custard"));
        List<SlotMeasurementRequest> sanitisedMeasurements = List.of(new SlotMeasurementRequest(A1, "Alpha", "Beta", 1));

        mayAddProblem("Bad address").when(mockOwsmService).validateAddresses(any(), any(), any(), any());
        when(mockOwsmService.loadOpType(any(), any())).thenReturn(opType);
        when(mockOwsmService.validateComments(any(), any())).thenReturn(validatedComments);
        when(mockOwsmService.sanitiseMeasurements(any(), any(), any())).thenReturn(sanitisedMeasurements);
        mayAddProblem("Bad measurement").when(mockOwsmService).checkForDupeMeasurements(any(), any());

        service.owsmValidate(data);
        verify(mockOwsmService).validateAddresses(data.problems, data.destLabwareType, Set.of(A1, A2), data.request.getSlotMeasurements());
        verify(mockOwsmService).loadOpType(data.problems, "Amplification");
        verify(mockOwsmService).validateComments(data.problems, data.request.getSlotMeasurements());
        verify(mockOwsmService).sanitiseMeasurements(data.problems, opType, data.request.getSlotMeasurements());
        verify(mockOwsmService).checkForDupeMeasurements(data.problems, sanitisedMeasurements);

        assertSame(data.ampOpType, opType);
        assertSame(data.comments, validatedComments);
        assertSame(data.sanitisedMeasurements, sanitisedMeasurements);
        assertThat(data.problems).containsExactlyInAnyOrder("Bad address", "Bad measurement");
    }
}