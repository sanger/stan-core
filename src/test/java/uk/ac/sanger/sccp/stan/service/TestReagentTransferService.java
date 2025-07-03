package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentAction;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.stan.service.operation.BioStateReplacer;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;

/**
 * Tests {@link ReagentTransferServiceImp}
 */
public class TestReagentTransferService {
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private ReagentActionRepo mockReagentActionRepo;
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private ReagentTransferValidatorService mockValService;
    @Mock
    private LabwareValidatorFactory mockLwValFactory;
    @Mock
    private BioRiskService mockBioRiskService;
    @Mock
    private OperationService mockOpService;
    @Mock
    private ReagentPlateService mockReagentPlateService;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private BioStateReplacer mockBioStateReplacer;

    private ReagentTransferServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(new ReagentTransferServiceImp(mockOpTypeRepo, mockReagentActionRepo, mockLwRepo,
                mockValService, mockLwValFactory, mockBioRiskService, mockOpService, mockReagentPlateService,
                mockWorkService, mockBioStateReplacer));
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testPerform(boolean valid) {
        User user = EntityFactory.getUser();
        Labware lw = valid ? EntityFactory.getTube() : null;
        String plateType = ReagentPlate.REAGENT_PLATE_TYPES.get(0);
        ReagentPlate rp = new ReagentPlate("001", plateType);
        Work work = new Work(10, "SGP10", null, null, null, null, null, Work.Status.active);
        OperationType opType = valid ? EntityFactory.makeOperationType("Fry", null) : null;
        ReagentTransferRequest request = new ReagentTransferRequest("fry", work.getWorkNumber(), valid ? lw.getBarcode() : "STAN-404",
                List.of(new ReagentTransfer(rp.getBarcode(), new Address(1,2), new Address(3,4))), plateType);
        UCMap<ReagentPlate> rpMap = UCMap.from(ReagentPlate::getBarcode, rp);

        if (valid) {
            doReturn(opType).when(service).loadOpType(any(), any());
        } else {
            doAnswer(addProblem("Bad op type")).when(service).loadOpType(any(), any());
        }
        if (valid) {
            doReturn(lw).when(service).loadLabware(any(), any());
        } else {
            doAnswer(addProblem("Bad lw")).when(service).loadLabware(any(), any());
        }
        when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        doReturn(rpMap).when(service).loadReagentPlates(any());
        if (valid) {
            doNothing().when(service).validateTransfers(any(), any(), any(), any());
        } else {
            doAnswer(addProblem("Bad transfers")).when(service).validateTransfers(any(), any(), any(), any());
        }

        if (valid) {
            OperationResult opRes = new OperationResult(List.of(), List.of(lw));
            doReturn(opRes).when(service).record(any(), any(), any(), any(), any(), any(), any());

            assertSame(opRes, service.perform(user, request));
        } else {
            assertValidationException(() -> service.perform(user, request), "The request could not be validated.",
                    "Bad op type", "Bad lw", "Bad transfers");
        }
        verify(service).loadOpType(anyCollection(), eq(request.getOperationType()));
        verify(service).loadLabware(anyCollection(), eq(request.getDestinationBarcode()));
        verify(mockWorkService).validateUsableWork(anyCollection(), eq(request.getWorkNumber()));
        verify(service).loadReagentPlates(request.getTransfers());
        verify(service).validateTransfers(anyCollection(), same(request.getTransfers()), same(rpMap), same(lw));
        if (valid) {
            verify(service).record(user, opType, work, request.getTransfers(), rpMap, lw, plateType);
        } else {
            verify(service, never()).record(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "Fry, true, REAGENT_TRANSFER|IN_PLACE,",
            ", false,,No operation type specified.",
            "'', false,,No operation type specified.",
            "Fry, false,,Unknown operation type: \"Fry\"",
            "Fry, true,,X",
            "Fry, true, IN_PLACE,X",
            "Fry, true, REAGENT_TRANSFER,X",
            "Fry, true, REAGENT_TRANSFER|IN_PLACE|RESULT,X",
            "Fry, true, REAGENT_TRANSFER|IN_PLACE|ANALYSIS,X",
            "Fry, true, REAGENT_TRANSFER|IN_PLACE|SOURCE_IS_BLOCK,X",
    })
    public void testLoadOpType(String opName, boolean exists, String opFlagNames, String expectedProblem) {
        final List<String> problems = new ArrayList<>();
        if (!exists) {
            when(mockOpTypeRepo.findByName(opName)).thenReturn(Optional.empty());
            assertNull(service.loadOpType(problems, opName));
            assertThat(problems).containsExactly(expectedProblem);
            return;
        }

        OperationType opType;
        if (opFlagNames==null) {
            opType = EntityFactory.makeOperationType(opName, null);
        } else {
            OperationTypeFlag[] opFlags = Arrays.stream(opFlagNames.split("\\|"))
                    .map(OperationTypeFlag::valueOf)
                    .toArray(OperationTypeFlag[]::new);
            opType = EntityFactory.makeOperationType(opName, null, opFlags);
        }
        when(mockOpTypeRepo.findByName(opName)).thenReturn(Optional.of(opType));

        assertSame(opType, service.loadOpType(problems, opName));
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else if (expectedProblem.equals("X")) {
            assertThat(problems).containsExactly("Operation type "+opName+" cannot be used in this request.");
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "true,",
            "false,That barcode does not exist.",
            "true,That barcode is fail.",
    })
    public void testLoadLabware(boolean exists, String problem) {
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        Labware lw = (exists ? EntityFactory.getTube() : null);
        when(val.getErrors()).thenReturn(problem==null ? List.of() : List.of(problem));
        when(val.loadLabware(any(), any())).thenReturn(exists ? List.of(lw) : List.of());

        final String barcode = "STAN-ABC";
        final List<String> problems = new ArrayList<>();
        assertSame(lw, service.loadLabware(problems, barcode));

        verify(mockLwValFactory).getValidator();
        verify(val).loadLabware(mockLwRepo, List.of(barcode));
        verify(val).validateSources();
        verify(val).getErrors();

        if (problem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(problem);
        }
    }

    @Test
    public void testLoadReagentPlates() {
        List<ReagentTransfer> transfers = List.of(
                new ReagentTransfer("123", null, null),
                new ReagentTransfer("abc", null, null),
                new ReagentTransfer("ABC", null, null),
                new ReagentTransfer(null, null, null)
        );
        UCMap<ReagentPlate> plateUCMap = UCMap.from(ReagentPlate::getBarcode, new ReagentPlate("123", ReagentPlate.REAGENT_PLATE_TYPES.get(1)));
        when(mockReagentPlateService.loadPlates(any())).thenReturn(plateUCMap);

        assertSame(plateUCMap, service.loadReagentPlates(transfers));

        verify(mockReagentPlateService).loadPlates(Set.of("123", "ABC"));
    }

    @ParameterizedTest
    @MethodSource("checkPlateTypeArgs")
    public void testCheckPlateType(String plateTypeArg, Collection<ReagentPlate> existingPlates, String expectedResult,
                                   String expectedProblem) {
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        String result = service.checkPlateType(problems, existingPlates, plateTypeArg);
        assertEquals(expectedResult, result);
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    static Stream<Arguments> checkPlateTypeArgs() {
        String ffpe = ReagentPlate.REAGENT_PLATE_TYPES.get(1).toLowerCase();
        String ff = ReagentPlate.REAGENT_PLATE_TYPES.get(0).toLowerCase();
        ReagentPlate ffpePlate = new ReagentPlate("RP1", ReagentPlate.REAGENT_PLATE_TYPES.get(1));
        ReagentPlate frozenPlate = new ReagentPlate("RP2", ReagentPlate.REAGENT_PLATE_TYPES.get(0));
        return Arrays.stream(new Object[][]{
                {ffpe, List.of(), ReagentPlate.REAGENT_PLATE_TYPES.get(1), null},
                {ffpe, List.of(ffpePlate), ReagentPlate.REAGENT_PLATE_TYPES.get(1), null},
                {ff, List.of(), ReagentPlate.REAGENT_PLATE_TYPES.get(0), null},
                {ff, List.of(frozenPlate), ReagentPlate.REAGENT_PLATE_TYPES.get(0), null},
                {null, List.of(), null, "Unknown plate type: null"},
                {"bananas", List.of(ffpePlate), null, "Unknown plate type: \"bananas\""},
                {ffpe, List.of(ffpePlate, frozenPlate), ReagentPlate.REAGENT_PLATE_TYPES.get(1), "The given plate type "+ ReagentPlate.REAGENT_PLATE_TYPES.get(1) +" does " +
                        "not match the existing plate [RP2]."},
                {ff, List.of(ffpePlate, frozenPlate), ReagentPlate.REAGENT_PLATE_TYPES.get(0), "The given plate type "+ ReagentPlate.REAGENT_PLATE_TYPES.get(0) +" does " +
                        "not match the existing plate [RP1]."},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidateTransfers(boolean valid) {
        final String problem = valid ? null : "Bad transfers.";
        mayAddProblem(problem).when(mockValService).validateTransfers(any(), any(), any(), any());

        final List<String> problems = new ArrayList<>(valid ? 0 : 1);
        List<ReagentTransfer> transfers = List.of(
                new ReagentTransfer("RP1", new Address(1,1), new Address(1,2))
        );
        UCMap<ReagentPlate> rpMap = UCMap.from(ReagentPlate::getBarcode, EntityFactory.makeReagentPlate("RP1"));
        Labware lw = EntityFactory.getTube();
        service.validateTransfers(problems, transfers, rpMap, lw);
        verify(mockValService).validateTransfers(problems, transfers, rpMap, lw.getLabwareType());
        assertProblem(problems, problem);
    }

    @Test
    public void testRecord() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Fry", EntityFactory.getBioState());
        Work work = new Work(1, "SGP1", null, null, null, null, null, Work.Status.active);
        List<ReagentTransfer> transfers = List.of(new ReagentTransfer("123", new Address(1,2), new Address(3,4)));
        String plateType = ReagentPlate.REAGENT_PLATE_TYPES.get(1);
        UCMap<ReagentPlate> rpmap = UCMap.from(ReagentPlate::getBarcode, new ReagentPlate("123", plateType));
        Labware lw = EntityFactory.getTube();
        Sample sam = lw.getFirstSlot().getSamples().getFirst();
        List<Action> actions = List.of(new Action(null, null, lw.getFirstSlot(), lw.getFirstSlot(), sam, sam));
        Operation op = EntityFactory.makeOpForLabware(opType, List.of(lw), List.of(lw));
        doNothing().when(service).createReagentPlates(any(), any(), any());
        doReturn(actions).when(mockBioStateReplacer).updateBioStateInPlace(any(), any());
        doReturn(op).when(service).createOperation(any(), any(), any(), any(), any());
        doNothing().when(service).recordTransfers(any(), any(), any(), any());

        assertEquals(new OperationResult(List.of(op), List.of(lw)), service.record(user, opType, work, transfers, rpmap, lw, plateType));

        verify(service).createReagentPlates(transfers, rpmap, plateType);
        verify(mockBioStateReplacer).updateBioStateInPlace(opType.getNewBioState(), lw);
        verify(service).createOperation(user, opType, work, lw, actions);
        verify(service).recordTransfers(transfers, rpmap, lw, op.getId());
    }

    @Test
    public void testCreateReagentPlates() {
        ReagentPlate[] rps = IntStream.range(0, 3)
                .mapToObj(i -> EntityFactory.makeReagentPlate("00"+i))
                .toArray(ReagentPlate[]::new);
        UCMap<ReagentPlate> rpMap = UCMap.from(ReagentPlate::getBarcode, rps[0]);
        List<ReagentTransfer> transfers = List.of(
                new ReagentTransfer(rps[0].getBarcode(), null, null),
                new ReagentTransfer(rps[0].getBarcode(), null, null),
                new ReagentTransfer(rps[1].getBarcode(), null, null),
                new ReagentTransfer(rps[1].getBarcode(), null, null),
                new ReagentTransfer(rps[2].getBarcode(), null, null)
        );
        String plateType = ReagentPlate.REAGENT_PLATE_TYPES.get(1);
        for (int i = 1; i < 3; ++i) {
            when(mockReagentPlateService.createReagentPlate(rps[i].getBarcode(), plateType)).thenReturn(rps[i]);
        }

        service.createReagentPlates(transfers, rpMap, plateType);

        assertThat(rpMap).hasSize(rps.length);
        for (ReagentPlate rp : rps) {
            assertEquals(rp, rpMap.get(rp.getBarcode()));
        }
        verify(mockReagentPlateService, times(2)).createReagentPlate(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "true,true"})
    public void testCreateOperation(boolean withActions, boolean withWork) {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Fry", null);
        Labware lw = EntityFactory.getTube();

        List<Action> actions;
        if (withActions) {
            Slot slot = lw.getFirstSlot();
            Sample sample = slot.getSamples().getFirst();
            actions = List.of(new Action(null,  null, slot, slot, sample, sample));
        } else {
            actions = null;
        }
        Work work = (withWork ? new Work(10, "SGP10", null, null, null, null, null, Work.Status.active) : null);

        Operation op = new Operation(10, opType, null, null, user);
        if (withActions) {
            when(mockOpService.createOperation(any(), any(), any(), any())).thenReturn(op);
        } else {
            when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);
        }

        assertSame(op, service.createOperation(user, opType, work, lw, actions));

        if (withActions) {
            verify(mockOpService).createOperation(opType, user, actions, null);
        } else {
            verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        }
        verifyNoMoreInteractions(mockOpService);
        if (withWork) {
            verify(mockWorkService).link(work, List.of(op));
        } else {
            verifyNoInteractions(mockWorkService);
        }
        verify(mockBioRiskService).copyOpSampleBioRisks(op);
    }

    @Test
    public void testRecordTransfers() {
        final Integer opId = 500;
        ReagentPlate rp = EntityFactory.makeReagentPlate("123");
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(2,2));
        final Address A1 = new Address(1, 1);
        final Address B1 = new Address(2, 1);
        final Address A2 = new Address(1, 2);
        List<ReagentTransfer> transfers = List.of(
                new ReagentTransfer("123", A1, A1),
                new ReagentTransfer("123", B1, A2)
        );
        service.recordTransfers(transfers, UCMap.from(ReagentPlate::getBarcode, rp), lw, opId);
        List<ReagentAction> expectedActions = List.of(
                new ReagentAction(null, opId, rp.getSlot(A1), lw.getSlot(A1)),
                new ReagentAction(null, opId, rp.getSlot(B1), lw.getSlot(A2))
        );
        verify(mockReagentActionRepo).saveAll(expectedActions);
    }
}