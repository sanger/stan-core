package uk.ac.sanger.sccp.stan.service.operation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.OperationTypeRepo;
import uk.ac.sanger.sccp.stan.request.InPlaceOpRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link InPlaceOpServiceImp}
 * @author dr6
 */
public class TestInPlaceOpService {
    private InPlaceOpServiceImp service;

    private LabwareValidatorFactory mockLabwareValidatorFactory;
    private BioRiskService mockBioRiskService;
    private OperationService mockOpService;
    private WorkService mockWorkService;
    private BioStateReplacer mockBioStateReplacer;
    private OperationTypeRepo mockOpTypeRepo;
    private LabwareRepo mockLwRepo;
    private ValidationHelperFactory valFactory;
    private ValidationHelper mockVal;

    @BeforeEach
    void setup() {
        mockLabwareValidatorFactory = mock(LabwareValidatorFactory.class);
        mockBioRiskService = mock(BioRiskService.class);
        mockOpService = mock(OperationService.class);
        mockWorkService = mock(WorkService.class);
        mockBioStateReplacer = mock(BioStateReplacer.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        valFactory = mock(ValidationHelperFactory.class);
        mockVal = mock(ValidationHelper.class);

        service = spy(new InPlaceOpServiceImp(mockLabwareValidatorFactory, mockOpService, mockBioRiskService,
                mockWorkService,
                mockBioStateReplacer, mockOpTypeRepo, mockLwRepo, valFactory));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testRecord(boolean successful) {
        List<Labware> labware = List.of(EntityFactory.getTube());
        OperationType opType = new OperationType(10, "Bananas");
        Equipment equipment = new Equipment("Feniks", "scanner");
        Work work = new Work(20, "SGP2000", null, null, null, null, null, Work.Status.active);

        InPlaceOpRequest request = new InPlaceOpRequest(opType.getName(), List.of(labware.get(0).getBarcode()),
                equipment.getId(), List.of(work.getWorkNumber()));

        doReturn(labware).when(service).validateLabware(any(), any());
        doReturn(opType).when(service).validateOpType(any(), any(), any());
        doReturn(mockVal).when(valFactory).getHelper();
        doReturn(equipment).when(mockVal).checkEquipment(any(), any());
        final String problem = "Everything is bad.";
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work);
        if (successful) {
            when(mockWorkService.validateWorksForOpType(any(), any(), any())).thenReturn(workMap);
            doReturn(Set.of()).when(mockVal).getProblems();
        } else {
            when(mockWorkService.validateWorksForOpType(any(), any(), any())).then(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add(problem);
                return workMap;
            });
            doReturn(Set.of("Bad equipment.")).when(mockVal).getProblems();
        }
        OperationResult result = new OperationResult();
        doReturn(result).when(service).createOperations(any(), any(), any(), any(), any());

        User user = EntityFactory.getUser();
        if (successful) {
            assertSame(result, service.record(user, request));
        } else {
            ValidationException exception = assertThrows(ValidationException.class, () -> service.record(user, request));
            //noinspection unchecked
            assertThat((Collection<String>) exception.getProblems()).containsExactlyInAnyOrder(problem, "Bad equipment.");
        }

        verify(service).validateLabware(any(), eq(request.getBarcodes()));
        verify(service).validateOpType(any(), eq(request.getOperationType()), eq(labware));
        verify(mockWorkService).validateWorksForOpType(any(), eq(request.getWorkNumbers()), same(opType));
        verify(mockVal).checkEquipment(request.getEquipmentId(), null);
        verify(service, times(successful ? 1 : 0)).createOperations(user, labware, opType, equipment, workMap.values());
    }

    @Test
    public void testValidateLabware() {
        Labware lw = EntityFactory.getTube();
        List<Labware> labware = List.of(lw);
        LabwareValidator mockVal = mock(LabwareValidator.class);
        when(mockVal.getLabware()).thenReturn(labware);
        String problem = "Labware is bad.";
        when(mockVal.getErrors()).thenReturn(List.of(problem));
        when(mockLabwareValidatorFactory.getValidator()).thenReturn(mockVal);

        List<String> barcodes = List.of(lw.getBarcode());
        List<String> problems = new ArrayList<>();

        assertSame(labware, service.validateLabware(problems, barcodes));
        assertThat(problems).containsExactly(problem);

        verify(mockVal).setUniqueRequired(true);
        verify(mockVal).loadLabware(mockLwRepo, barcodes);
        verify(mockVal).validateSources();
    }

    @ParameterizedTest
    @MethodSource("validateOpTypeArgs")
    public void testValidateOpType(Object opTypeObj, Object labwareObj, Object expectedProblemsObj) {
        String opTypeName;
        OperationType opType;
        if (opTypeObj instanceof OperationType) {
            opType = (OperationType) opTypeObj;
            opTypeName = opType.getName();
        } else {
            opType = null;
            opTypeName = (String) opTypeObj;
        }

        List<Labware> labware = EntityFactory.objToList(labwareObj);
        List<String> expectedProblems = EntityFactory.objToList(expectedProblemsObj);
        List<String> problems = new ArrayList<>(expectedProblems.size());

        when(mockOpTypeRepo.findByName(opTypeName)).thenReturn(Optional.ofNullable(opType));

        assertSame(opType, service.validateOpType(problems, opTypeName, labware));
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateOpTypeArgs() {
        OperationType ot = EntityFactory.makeOperationType("Scan", null, OperationTypeFlag.IN_PLACE);
        OperationType ot2 = EntityFactory.makeOperationType("Passage", null);
        OperationType ot3 = EntityFactory.makeOperationType("Blodge", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.SOURCE_IS_BLOCK);
        LabwareType lt = EntityFactory.getTubeType();
        Sample sam = EntityFactory.getSample();
        Labware block = EntityFactory.makeLabware(lt, sam);
        block.getFirstSlot().setBlockSampleId(sam.getId());
        Labware tube = EntityFactory.makeLabware(lt, sam);
        return Arrays.stream(new Object[][] {
                {ot, tube, null},
                {null, tube, "No operation type specified."},
                {"", tube, "No operation type specified."},
                {"Bananas", tube, "Unknown operation type: \"Bananas\""},
                {ot2, tube, "Operation type Passage cannot be recorded in place."},
                {ot3, tube, "Operation type Blodge can only be recorded on a block."},
        }).map(Arguments::of);
    }
    @Test
    public void testMakeActions_nobs() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, 500, sam1.getTissue(), sam1.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(3,1);
        Labware lw = EntityFactory.makeLabware(lt, sam1, sam2);
        Slot slot1 = lw.getFirstSlot();
        Slot slot2 = lw.getSlot(new Address(2,1));
        slot1.setSamples(List.of(sam1, sam2));
        assertThat(service.makeActions(null, lw)).containsExactlyInAnyOrder(
                new Action(null, null, slot1, slot1, sam1, sam1),
                new Action(null, null, slot1, slot1, sam2, sam2),
                new Action(null, null, slot2, slot2, sam2, sam2)
        );
        verifyNoInteractions(mockBioStateReplacer);
    }

    @Test
    public void testMakeActions_bs() {
        Labware lw = EntityFactory.getTube();
        Slot slot = lw.getFirstSlot();
        Sample sample = slot.getSamples().get(0);
        BioState bs = EntityFactory.getBioState();
        List<Action> actions = List.of(new Action(null, null, slot, slot, sample, sample));
        when(mockBioStateReplacer.updateBioStateInPlace(any(), any())).thenReturn(actions);

        assertSame(actions, service.makeActions(bs, lw));
        verify(mockBioStateReplacer).updateBioStateInPlace(bs, lw);
    }

    @Test
    public void testCreateOperation() {
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        Slot slot = lw.getFirstSlot();
        List<Action> actions = List.of(new Action(null, null, slot, slot, sample, sample));
        OperationType opType = EntityFactory.makeOperationType("Scan", EntityFactory.getBioState(), OperationTypeFlag.IN_PLACE);
        User user = EntityFactory.getUser();
        doReturn(actions).when(service).makeActions(any(), any());
        //noinspection unchecked
        Consumer<Operation> opModifier = mock(Consumer.class);
        Operation op = new Operation();

        when(mockOpService.createOperation(any(), any(), any(), any(), any())).thenReturn(op);

        assertSame(op, service.createOperation(user, lw, opType, opModifier));
        verify(service).makeActions(opType.getNewBioState(), lw);
        verify(mockOpService).createOperation(opType, user, actions, null, opModifier);
    }

    @ParameterizedTest
    @MethodSource("createOperationsArgs")
    public void testCreateOperations(Collection<Labware> labware, OperationType opType,
                                     Equipment equipment, Work work) {
        final List<Operation> ops = new ArrayList<>(labware.size());
        doAnswer(invocation -> {
            Operation op = new Operation();
            Consumer<Operation> opModifier = invocation.getArgument(3);
            if (opModifier!=null) {
                opModifier.accept(op);
            }
            ops.add(op);
            return op;
        }).when(service).createOperation(any(), any(), any(), any());

        User user = EntityFactory.getUser();
        List<Work> works = (work==null ? List.of() : List.of(work));
        OperationResult result = service.createOperations(user, labware, opType, equipment, works);

        for (Labware lw : labware) {
            verify(service).createOperation(eq(user), eq(lw), eq(opType), equipment==null ? isNull():isNotNull());
        }
        assertEquals(result, new OperationResult(ops, labware));
        for (Operation op : ops) {
            assertEquals(equipment, op.getEquipment());
        }
        if (work==null) {
            verifyNoInteractions(mockWorkService);
        } else {
            verify(mockWorkService).link(work, ops, opType.supportsAnyOpenWork());
        }
        verify(mockBioRiskService).copyOpSampleBioRisks(ops);
    }

    static Stream<Arguments> createOperationsArgs() {
        OperationType opType = new OperationType(10, "Scan");
        Equipment equipment = new Equipment(20, "Feenicks", "scanner", true);
        Work work = new Work(30, "SGP3000", null, null, null, null, null, Work.Status.active);
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        List<Labware> labware = List.of(lw1, lw2);
        return Arrays.stream(new Object[][] {
                {equipment, work},
                {null, work},
                {equipment,null},
                {null,null},
        }).map(arr -> Arguments.of(labware, opType, arr[0], arr[1]));
    }
}
