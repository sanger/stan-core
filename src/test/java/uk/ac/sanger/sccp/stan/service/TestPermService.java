package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.RecordPermRequest.PermData;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Tests {@link PermServiceImp}
 * @author dr6
 */
public class TestPermService {
    private LabwareValidatorFactory mockLabwareValidatorFactory;
    private OperationService mockOpService;
    private WorkService mockWorkService;
    private LabwareRepo mockLabwareRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private OperationRepo mockOpRepo;
    private MeasurementRepo mockMeasurementRepo;

    private PermServiceImp service;

    @BeforeEach
    void setup() {
        mockLabwareValidatorFactory = mock(LabwareValidatorFactory.class);
        mockOpService = mock(OperationService.class);
        mockWorkService = mock(WorkService.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpRepo = mock(OperationRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);

        service = spy(new PermServiceImp(mockLabwareValidatorFactory, mockOpService,
                mockWorkService, mockLabwareRepo, mockOpTypeRepo, mockOpRepo, mockMeasurementRepo));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testRecordPerm(boolean valid) {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        RecordPermRequest request = new RecordPermRequest(lw.getBarcode(), List.of(new PermData(new Address(1,1), 17)), "SGP-2000");
        doReturn(lw).when(service).lookUpLabware(any(), any());
        doNothing().when(service).validateLabware(any(), any());
        Work work = new Work(300, "SGP-2000", null, null, null, Work.Status.active);
        when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        if (valid) {
            doNothing().when(service).validatePermData(any(), any(), any());
        } else {
            doAnswer(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add("Perm problem.");
                return null;
            }).when(service).validatePermData(any(), any(), any());
        }
        OperationResult result = new OperationResult(List.of(), List.of(lw));
        doReturn(result).when(service).record(any(), any(), any(), any());

        if (valid) {
            assertSame(result, service.recordPerm(user, request));
        } else {
            var ex = assertThrows(ValidationException.class, () -> service.recordPerm(user, request));
            assertThat(ex).hasMessage("The request could not be validated.");
            //noinspection unchecked
            assertThat((Collection<Object>) ex.getProblems()).containsExactly("Perm problem.");
        }
        //noinspection unchecked
        ArgumentCaptor<Collection<String>> problemsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(service).lookUpLabware(problemsCaptor.capture(), same(request.getBarcode()));
        Collection<String> problems = problemsCaptor.getValue();
        verify(service).validateLabware(same(problems), same(lw));
        verify(service).validatePermData(same(problems), same(lw), same(request.getPermData()));
        verify(mockWorkService).validateUsableWork(same(problems), same(request.getWorkNumber()));

        if (valid) {
            verify(service).record(user, lw, request.getPermData(), work);
        } else {
            verify(service, never()).record(any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"", "null", "STAN-100", "STAN-404"})
    public void testLookUpLabware(String barcode) {
        if (barcode.equals("null")) {
            barcode = null;
        }
        Labware lw;
        if (barcode!=null && barcode.equals("STAN-100")) {
            lw = EntityFactory.getTube();
        } else {
            lw = null;
        }
        when(mockLabwareRepo.findByBarcode(any())).thenReturn(Optional.ofNullable(lw));
        List<String> problems = new ArrayList<>(lw==null ? 1 : 0);
        assertSame(lw, service.lookUpLabware(problems, barcode));

        if (barcode==null || barcode.isEmpty()) {
            assertThat(problems).containsExactly("No barcode specified.");
        } else if (lw==null) {
            assertThat(problems).containsExactly("Unknown labware barcode: \""+barcode+"\"");
        } else {
            assertThat(problems).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({
            ", false, false, false, false",
            "Bad labware., true, false, false, false",
            "Stain operation type not found in database., true, true, false, false",
            "Stain has not been recorded on labware [BARCODE]., true, true, true, false",
            ", true, true, true, true",
    })
    public void testValidateLabware(String expectedProblem,
                                    boolean lwPresent, boolean validSource, boolean opTypeExists, boolean opExists) {
        final List<String> problems = new ArrayList<>(1);
        if (!lwPresent) {
            service.validateLabware(problems, null);
            assertThat(problems).isEmpty();
            verifyNoInteractions(mockLabwareValidatorFactory);
            verifyNoInteractions(mockOpTypeRepo);
            verifyNoInteractions(mockOpRepo);
            return;
        }
        Labware lw = EntityFactory.getTube();
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLabwareValidatorFactory.getValidator(any())).thenReturn(val);

        if (!validSource) {
            when(val.getErrors()).thenReturn(List.of(expectedProblem));
            service.validateLabware(problems, lw);
            assertThat(problems).containsExactly(expectedProblem);
            verify(mockLabwareValidatorFactory).getValidator(List.of(lw));
            verify(val).validateSources();
            verify(val).getErrors();
            return;
        }
        when(val.getErrors()).thenReturn(List.of());

        OperationType opType = opTypeExists ? EntityFactory.makeOperationType("Stain", null) : null;
        when(mockOpTypeRepo.findByName(any())).thenReturn(Optional.ofNullable(opType));
        List<Operation> ops;
        if (opExists) {
            Operation op = new Operation();
            op.setId(17);
            ops = List.of(op);
        } else {
            expectedProblem = expectedProblem.replace("[BARCODE]", lw.getBarcode());
            ops = List.of();
        }
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(ops);

        service.validateLabware(problems, lw);
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }

        verify(mockLabwareValidatorFactory).getValidator(List.of(lw));
        verify(val).validateSources();
        verify(val).getErrors();
        verify(mockOpTypeRepo).findByName("Stain");
        if (!opTypeExists) {
            verifyNoInteractions(mockOpRepo);
        } else {
            verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()));
        }
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "true, true"})
    public void testValidatePermData(boolean lwPresent, boolean dataPresent) {
        List<PermData> permData = (dataPresent ? List.of(new PermData(new Address(1,1), 17)) : List.of());
        final List<String> problems = new ArrayList<>();
        if (!lwPresent) {
            service.validatePermData(problems, null, permData);
            verify(service, never()).validateAddresses(any(), any(), any());
            verify(service, never()).validatePermValues(any(), any());
            assertThat(problems).isEmpty();
            return;
        }
        Labware lw = EntityFactory.getTube();
        if (!dataPresent) {
            service.validatePermData(problems, lw, permData);
            verify(service, never()).validateAddresses(any(), any(), any());
            verify(service, never()).validatePermValues(any(), any());
            assertThat(problems).containsExactly("No permabilisation data provided.");
            return;
        }
        doAnswer(invocation -> {
            Collection<String> p = invocation.getArgument(0);
            p.add("Address validation problem.");
            return null;
        }).when(service).validateAddresses(any(), any(), any());
        doAnswer(invocation -> {
            Collection<String> p = invocation.getArgument(0);
            p.add("Perm value validation problem.");
            return null;
        }).when(service).validatePermValues(any(), any());

        service.validatePermData(problems, lw, permData);
        assertThat(problems).containsExactly("Address validation problem.", "Perm value validation problem.");
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidateAddresses(boolean allValid) {
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(lt, sample, sample);
        lw.setBarcode("STAN-10");

        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        if (allValid) {
            List<String> problems = new ArrayList<>();
            List<PermData> permData = List.of(
                    new PermData(A1, 17),
                    new PermData(A2, ControlType.negative)
            );
            service.validateAddresses(problems, lw, permData);
            assertThat(problems).isEmpty();
            return;
        }
        List<PermData> permData = List.of(
                new PermData(A1, 17),
                new PermData(A2, 21),
                new PermData(A2, 31),
                new PermData(null, 43),
                new PermData(new Address(2,1), 51),
                new PermData(new Address(2,2), 52),
                new PermData(new Address(3,5), 10),
                new PermData(new Address(3,6), 11)
        );
        List<String> problems = new ArrayList<>(4);
        service.validateAddresses(problems, lw, permData);
        assertThat(problems).containsExactlyInAnyOrder(
                "Missing slot address in perm data.",
                "Invalid slot address for labware STAN-10: [C5, C6]",
                "Repeated slot address: [A2]",
                "Indicated slot is empty: [B1, B2]"
        );
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidatePermValues(boolean allValid) {
        List<PermData> permData;
        String[] expectedProblems;
        if (allValid) {
            permData = List.of(
                    new PermData(new Address(1,2), 17),
                    new PermData(new Address(2,3), ControlType.negative)
            );
            expectedProblems = new String[0];
        } else {
            permData = List.of(
                    new PermData(null, null, null),
                    new PermData(new Address(1,2), null, null),
                    new PermData(new Address(1,3), null, null),
                    new PermData(new Address(2,3), 17, ControlType.negative),
                    new PermData(new Address(2,4), 18, ControlType.positive)
            );
            expectedProblems = new String[] {
                    "Neither control type nor time specified for the given address: [A2, A3]",
                    "Control type and time specified for the same address: [B3, B4]",
            };
        }
        List<String> problems = new ArrayList<>(expectedProblems.length);
        service.validatePermValues(problems, permData);
        assertThat(problems).containsExactlyInAnyOrder(expectedProblems);
    }

    @Test
    public void testRecord() {
        OperationType opType = EntityFactory.makeOperationType("Visium permabilisation", null);
        Work work = new Work(14, "SGP14", null, null, null, Work.Status.active);
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        List<PermData> permData = List.of(new PermData(new Address(1,1), 16));
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        Operation op = new Operation();
        op.setId(37);
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);
        doNothing().when(service).createMeasurements(any(), any(), any());

        OperationResult result = service.record(user, lw, permData, work);
        assertThat(result.getOperations()).containsExactly(op);
        assertThat(result.getLabware()).containsExactly(lw);
        verify(mockOpTypeRepo).getByName(opType.getName());
        verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        verify(service).createMeasurements(op.getId(), lw, permData);
        verify(mockWorkService).link(work, List.of(op));
    }

    @Test
    public void testCreateMeasurements() {
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, 7, sam1.getTissue(), sam1.getBioState());
        Labware lw = EntityFactory.makeLabware(lt, sam1, sam2, sam1);
        lw.getFirstSlot().getSamples().add(sam2);
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        List<PermData> permData = List.of(
                new PermData(A1, 4),
                new PermData(A2, ControlType.positive)
        );
        Integer opId = 400;
        Integer slot1id = lw.getSlot(A1).getId();
        Integer slot2id = lw.getSlot(A2).getId();
        Integer sam1id = sam1.getId();
        Integer sam2id = sam2.getId();
        List<Measurement> expectedMeasurements = List.of(
                new Measurement(null, "permabilisation time", "4", sam1id, opId, slot1id),
                new Measurement(null, "permabilisation time", "4", sam2id, opId, slot1id),
                new Measurement(null, "control", "positive", sam2id, opId, slot2id)
        );

        service.createMeasurements(opId, lw, permData);
        verify(mockMeasurementRepo).saveAll(expectedMeasurements);
    }
}
