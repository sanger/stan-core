package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.LibraryConServiceImp.RequestData;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Test {@link LibraryConValidationServiceImp} */
class TestLibraryConValidationService {
    @Mock
    ValidationHelperFactory mockHelperFactory;
    @Mock
    ReagentTransferValidatorService mockRtValService;
    @Mock
    ReagentTransferService mockRtService;
    @Mock
    OpWithSlotMeasurementsService mockOwsmService;
    @InjectMocks
    LibraryConValidationServiceImp service;

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
        RequestData data = new RequestData(new LibraryConRequest(), new User(), new HashSet<>());

        doNothing().when(service).initValidate(any());
        doNothing().when(service).rtValidate(any());
        doNothing().when(service).owsmValidate(any());

        service.validate(data);

        verify(service).initValidate(data);
        verify(service).rtValidate(data);
        verify(service).owsmValidate(data);
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,false", "false,true"})
    void testInitValidate(boolean valid, boolean missingBarcode) {
        ValidationHelper helper = mock(ValidationHelper.class);
        when(mockHelperFactory.getHelper()).thenReturn(helper);
        LibraryConRequest request = new LibraryConRequest();
        User user = EntityFactory.getUser();
        Collection<String> problems = new HashSet<>();
        RequestData data = new RequestData(request, user, problems);
        Labware lw;
        if (valid) {
            lw = EntityFactory.getTube();
            request.setLabwareBarcode(lw.getBarcode());
        } else {
            lw = null;
            request.setLabwareBarcode(missingBarcode ? "" : "STAN-404");
        }
        UCMap<Labware> lwMap = valid ? UCMap.from(Labware::getBarcode, lw) : new UCMap<>();
        request.setWorkNumber("SGP1");
        Work work = valid ? EntityFactory.makeWork(request.getWorkNumber()) : null;
        when(helper.checkLabware(any())).thenReturn(lwMap);
        when(helper.checkWork(anyString())).thenReturn(work);

        Set<String> helperProblems = (valid ? Set.of() : missingBarcode ? Set.of("Bad work.") : Set.of("Bad barcode.", "Bad work."));
        when(helper.getProblems()).thenReturn(helperProblems);

        service.initValidate(data);
        assertThat(data.problems).containsAll(helperProblems);
        if (missingBarcode) {
            assertThat(data.problems).contains("No labware barcode supplied.");
            assertThat(data.problems).hasSize(helperProblems.size() + 1);
        }
        assertSame(lw, data.labware);
        assertSame(work, data.work);
        if (missingBarcode) {
            verify(helper, never()).checkLabware(any());
        } else {
            verify(helper).checkLabware(List.of(request.getLabwareBarcode()));
        }
        verify(helper).checkWork(request.getWorkNumber());
    }

    @Test
    void testRtValidate() {
        OperationType ot = EntityFactory.makeOperationType("Dual index plate", null);
        when(mockRtService.loadOpType(any(), anyString())).thenReturn(ot);
        ReagentPlate rp = new ReagentPlate("12345", ReagentPlate.REAGENT_PLATE_TYPES.getFirst());
        UCMap<ReagentPlate> rpMap = UCMap.from(ReagentPlate::getBarcode, rp);
        LibraryConRequest request = new LibraryConRequest();
        request.setReagentPlateType(ReagentPlate.REAGENT_PLATE_TYPES.getLast());
        Address A1 = new Address(1,1);
        request.setReagentTransfers(List.of(new ReagentTransferRequest.ReagentTransfer("rp1", A1, A1)));
        Labware lw = EntityFactory.getTube();
        Set<String> problems = new HashSet<>();
        when(mockRtService.loadOpType(any(), anyString())).thenReturn(ot);
        when(mockRtService.loadReagentPlates(any())).thenReturn(rpMap);
        when(mockRtService.checkPlateType(any(), any(), any())).thenReturn(ReagentPlate.REAGENT_PLATE_TYPES.getFirst());

        RequestData data = new RequestData(request, null, problems);
        data.labware = lw;
        service.rtValidate(data);
        assertSame(ot, data.reagentOpType);
        assertSame(rpMap, data.reagentPlates);
        assertEquals(ReagentPlate.REAGENT_PLATE_TYPES.getFirst(), data.reagentPlateType);

        verify(mockRtService).loadOpType(same(problems), eq("Dual index plate"));
        verify(mockRtService).checkPlateType(same(problems), eq(rpMap.values()), same(request.getReagentPlateType()));
        verify(mockRtValService).validateTransfers(same(problems), same(request.getReagentTransfers()), same(rpMap), eq(lw.layout()));
    }

    @Test
    void testRtValidate_nolw() {
        RequestData data = new RequestData(null, null, null);
        service.rtValidate(data);
        verifyNoInteractions(mockRtValService);
    }

    @Test
    void testOwsmValidate() {
        Address A1 = new Address(1,1), A3 = new Address(1,3);
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        Sample sample = EntityFactory.getSample();
        Set<Address> filledAddresses = Set.of(A1, A3);
        filledAddresses.forEach(ad -> lw.getSlot(ad).addSample(sample));
        OperationType ot = EntityFactory.makeOperationType("Amplification", null);
        when(mockOwsmService.loadOpType(any(), anyString())).thenReturn(ot);
        List<Comment> comments = List.of(new Comment(1, "com1", "cat"), new Comment(2, "com2", "cat"));
        when(mockOwsmService.validateComments(any(), any())).thenReturn(comments);
        List<SlotMeasurementRequest> sanMeas = List.of(new SlotMeasurementRequest(A1, "name", "val", List.of(1,2)));
        when(mockOwsmService.sanitiseMeasurements(any(), any(), any())).thenReturn(sanMeas);
        LibraryConRequest request = new LibraryConRequest();
        request.setSlotMeasurements(List.of(new SlotMeasurementRequest(A1, "NAME", "VAL", List.of(1))));
        Set<String> problems = new HashSet<>();
        RequestData data = new RequestData(request, null, problems);
        data.labware = lw;

        service.owsmValidate(data);
        assertSame(ot, data.ampOpType);
        assertSame(comments, data.comments);
        assertSame(sanMeas, data.sanitisedMeasurements);

        verify(mockOwsmService).validateAddresses(same(problems), eq(lw.layout()), eq(filledAddresses), same(request.getSlotMeasurements()));
        verify(mockOwsmService).loadOpType(same(problems), eq("Amplification"));
        verify(mockOwsmService).validateComments(same(problems), same(request.getSlotMeasurements()));
        verify(mockOwsmService).sanitiseMeasurements(same(problems), same(ot), same(request.getSlotMeasurements()));
        verify(mockOwsmService).checkForDupeMeasurements(same(problems), same(sanMeas));
    }

    @Test
    void testOwsmValidate_nolw() {
        RequestData data = new RequestData(null, null, null);
        service.owsmValidate(data);
        verifyNoInteractions(mockOwsmService);
    }
}
