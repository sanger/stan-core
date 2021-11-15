package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.VisiumAnalysisRequest;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link VisiumAnalysisServiceImp}
 */
public class TestVisiumAnalysisService {
    private LabwareValidatorFactory mockLabwareValidatorFactory;
    private WorkService mockWorkService;
    private OperationService mockOpService;
    private LabwareRepo mockLwRepo;
    private MeasurementRepo mockMeasurementRepo;
    private OperationTypeRepo mockOpTypeRepo;

    private VisiumAnalysisServiceImp service;

    @BeforeEach
    void setup() {
        mockLabwareValidatorFactory = mock(LabwareValidatorFactory.class);
        mockWorkService = mock(WorkService.class);
        mockOpService = mock(OperationService.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);

        service = spy(new VisiumAnalysisServiceImp(mockLabwareValidatorFactory, mockWorkService, mockOpService,
                mockLwRepo, mockMeasurementRepo, mockOpTypeRepo));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testRecord(boolean withWorkNumber) {
        User user = EntityFactory.getUser();
        String workNumber = (withWorkNumber ? "SGP500" : null);
        Work work = (withWorkNumber ?
                new Work(500, workNumber, null, null, null, Work.Status.active)
                : null);
        Labware lw = EntityFactory.getTube();
        String barcode = lw.getBarcode();
        final Address A1 = new Address(1,1);
        final Integer time = 240;
        VisiumAnalysisRequest request = new VisiumAnalysisRequest(barcode, workNumber, A1, time);

        doReturn(lw).when(service).loadLabware(any(), any());
        doNothing().when(service).validateMeasurement(any(), any(), any(), any());
        when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);

        OperationResult opres = new OperationResult(List.of(), List.of(lw));
        doReturn(opres).when(service).recordAnalysis(any(), any(), any(), any(), any());

        assertSame(opres, service.record(user, request));

        //noinspection unchecked
        ArgumentCaptor<Collection<String>> argCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(service).loadLabware(argCaptor.capture(), eq(barcode));
        Collection<String> problems = argCaptor.getValue();
        verify(mockWorkService).validateUsableWork(same(problems), eq(workNumber));
        verify(service).validateMeasurement(same(problems), same(lw), eq(A1), eq(time));

        verify(service).recordAnalysis(user, lw, A1, time, work);
    }

    @Test
    public void testRecordInvalid() {
        final String lwProblem = "Labware problem.";
        final String measProblem = "Measurement problem.";
        final String workProblem = "Work problem.";

        doAnswer(addProblem(lwProblem)).when(service).loadLabware(any(), any());
        when(mockWorkService.validateUsableWork(any(), any())).then(addProblem(workProblem));
        doAnswer(addProblem(measProblem)).when(service).validateMeasurement(any(), any(), any(), any());
        User user = EntityFactory.getUser();
        VisiumAnalysisRequest request = new VisiumAnalysisRequest("STAN-100", "SGP5000", new Address(1,1), 120);

        var exception = assertThrows(ValidationException.class, () -> service.record(user, request));
        assertThat(exception).hasMessage("The request could not be validated.");
        //noinspection unchecked
        assertThat((Collection<String>) exception.getProblems()).containsExactlyInAnyOrder(
                lwProblem, measProblem, workProblem
        );

        verify(service).loadLabware(any(), eq(request.getBarcode()));
        verify(mockWorkService).validateUsableWork(any(), eq(request.getWorkNumber()));
        verify(service).validateMeasurement(any(), isNull(), eq(request.getSelectedAddress()),
                eq(request.getSelectedTime()));

        verify(service, never()).recordAnalysis(any(), any(), any(), any(), any());
    }

    private static <X> Answer<X> addProblem(final String problem) {
        return invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            problems.add(problem);
            return null;
        };
    }

    @ParameterizedTest
    @MethodSource("loadLabwareArgs")
    public void testLoadLabware(String barcode, Labware lw, List<String> errors, List<String> expectedProblems) {
        final List<String> problems = new ArrayList<>(expectedProblems.size());
        if (barcode==null || barcode.isEmpty()) {
            assertNull(service.loadLabware(problems, barcode));
            assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
            verifyNoInteractions(mockLabwareValidatorFactory);
            return;
        }

        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLabwareValidatorFactory.getValidator()).thenReturn(val);
        when(val.getErrors()).thenReturn(errors);
        when(val.getLabware()).thenReturn(lw==null ? List.of() : List.of(lw));

        assertSame(lw, service.loadLabware(problems, barcode));
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);

        verify(val).loadLabware(mockLwRepo, List.of(barcode));
        verify(val).getLabware();
        verify(val).getErrors();
    }

    static Stream<Arguments> loadLabwareArgs() {
        Labware lw = EntityFactory.getTube();
        List<String> validationErrors = List.of("Invalid labware.");
        List<String> noBarcodeProblem = List.of("No barcode supplied.");
        return Arrays.stream(new Object[][] {
                {null, null, null, noBarcodeProblem},
                {"", null, null, noBarcodeProblem},
                {"STAN-404", null, validationErrors, validationErrors},
                {"STAN-100", lw, validationErrors, validationErrors},
                {"STAN-200", lw, List.of(), List.of()},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("validateMeasurementsArgs")
    public void testValidateMeasurements(Labware lw, Address address, Integer time, List<Measurement> measurements,
                                         String expectedProblem) {
        final List<Integer> slotIdList;
        if (measurements!=null) {
            slotIdList = List.of(lw.getSlot(address).getId());
            when(mockMeasurementRepo.findAllBySlotIdIn(slotIdList)).thenReturn(measurements);
        } else {
            slotIdList = null;
        }
        List<String> expectedProblems = (expectedProblem==null ? List.of() : List.of(expectedProblem));
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.validateMeasurement(problems, lw, address, time);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        if (measurements!=null) {
            verify(mockMeasurementRepo).findAllBySlotIdIn(slotIdList);
        }
    }

    static Stream<Arguments> validateMeasurementsArgs() {
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(lt, sample);
        lw.setBarcode("STAN-500");
        final Address A1 = new Address(1,1), A2 = new Address(1,2),
                A3 = new Address(1,3);
        List<Measurement> a1Measurements = List.of(
                new Measurement(10, "bananaas", "360", 10, 20, 30),
                new Measurement(11, "permabilisation time", "240", 10, 20, 30)
        );

        return Arrays.stream(new Object[][] {
                {lw, null, 240, null, "No slot address specified."},
                {null, A1, 240, null, null},
                {lw, A3, 240, null, "Slot A3 does not exist in labware STAN-500."},
                {lw, A2, 240, null, "There are no samples in slot A2 of labware STAN-500."},
                {lw, A1, null, null, "No selected time specified."},
                {lw, A1, 360, a1Measurements, "A permabilisation measurement of 360 seconds was not found " +
                        "in slot A1 of labware STAN-500."},
                {lw, A1, 240, a1Measurements, null},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testRecordAnalysis(boolean withWork) {
        OperationType opType = EntityFactory.makeOperationType("Visium analysis", null, OperationTypeFlag.IN_PLACE);
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        final Address A1 = new Address(1,1);
        Slot slot = lw.getSlot(A1);
        Operation op = new Operation(50, opType, null, null, null);
        when(mockOpService.createOperationInPlace(opType, user, lw, null, null)).thenReturn(op);
        doReturn(List.of()).when(service).createMeasurement(slot, "240", op.getId());
        Work work = (withWork ?
                new Work(20, "SGP20", null, null, null, Work.Status.active)
                : null);
        OperationResult opres = service.recordAnalysis(user, lw, A1, 240, work);
        assertThat(opres.getOperations()).containsExactly(op);
        assertThat(opres.getLabware()).containsExactly(lw);
        verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        verify(service).createMeasurement(slot, "240", op.getId());
        if (work==null) {
            verifyNoInteractions(mockWorkService);
        } else {
            verify(mockWorkService).link(work, List.of(op));
        }
    }

    @Test
    public void testCreateMeasurements() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, 17, sam1.getTissue(), sam1.getBioState());
        Slot slot = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType()).getFirstSlot();
        slot.setSamples(List.of(sam1, sam2));
        String value = "240";
        Integer opId = 88;
        Integer slotId = slot.getId();
        String name = "selected time";
        List<Measurement> savedMeasurements = List.of(
                new Measurement(100, name, value, sam1.getId(), opId, slotId),
                new Measurement(101, name, value, sam2.getId(), opId, slotId)
        );
        when(mockMeasurementRepo.saveAll(any())).thenReturn(savedMeasurements);

        assertSame(savedMeasurements, service.createMeasurement(slot, value, opId));
        verify(mockMeasurementRepo).saveAll(List.of(
                new Measurement(null, name, value, sam1.getId(), opId, slotId),
                new Measurement(null, name, value, sam2.getId(), opId, slotId)
        ));
    }
}