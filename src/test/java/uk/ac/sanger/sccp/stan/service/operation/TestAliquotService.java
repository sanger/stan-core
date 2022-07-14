package uk.ac.sanger.sccp.stan.service.operation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AliquotRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link AliquotServiceImp}
 * @author dr6
 */
public class TestAliquotService {
    private LabwareRepo mockLwRepo;
    private SlotRepo mockSlotRepo;
    private LabwareTypeRepo mockLwTypeRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private SampleRepo mockSampleRepo;
    private LabwareValidatorFactory mockLwValFactory;
    private WorkService mockWorkService;
    private LabwareService mockLwService;
    private OperationService mockOpService;

    private AliquotServiceImp service;
    private StoreService mockStoreService;
    private Transactor mockTransactor;

    @BeforeEach
    void setup() {
        mockLwRepo = mock(LabwareRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockLwTypeRepo = mock(LabwareTypeRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockLwValFactory = mock(LabwareValidatorFactory.class);
        mockWorkService = mock(WorkService.class);
        mockLwService = mock(LabwareService.class);
        mockOpService = mock(OperationService.class);
        mockStoreService = mock(StoreService.class);
        mockTransactor = mock(Transactor.class);

        service = spy(new AliquotServiceImp(mockLwRepo, mockLwTypeRepo, mockSlotRepo, mockOpTypeRepo, mockSampleRepo,
                mockLwValFactory, mockWorkService, mockLwService, mockOpService, mockStoreService, mockTransactor));
    }

    private static <R> Answer<R> addProblem(String problem, R returnValue) {
        return invocation -> {
            if (problem!=null) {
                Collection<String> problems = invocation.getArgument(0);
                problems.add(problem);
            }
            return returnValue;
        };
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true"})
    public void testPerform(boolean succeeds, boolean discard) {
        User user = EntityFactory.getUser();
        AliquotRequest request = new AliquotRequest();
        request.setOperationType("Aliquot");
        request.setBarcode("STAN-A1");
        OperationType opType = EntityFactory.makeOperationType("Aliquot", null);
        if (discard) {
            opType.setFlags(OperationTypeFlag.DISCARD_SOURCE.bit());
        }
        Operation op = new Operation();
        op.setOperationType(opType);
        OperationResult opres = new OperationResult(List.of(op), List.of());
        Matchers.mockTransactor(mockTransactor);

        if (succeeds) {
            doReturn(opres).when(service).performInTransaction(any(), any());
        } else {
            doThrow(ValidationException.class).when(service).performInTransaction(any(), any());
        }

        if (succeeds) {
            assertSame(opres, service.perform(user, request));
        } else {
            assertThrows(ValidationException.class, () -> service.perform(user, request));
        }
        verify(mockTransactor).transact(any(), any());
        verify(service).performInTransaction(user, request);
        if (succeeds && discard) {
            verify(mockStoreService).discardStorage(user, List.of("STAN-A1"));
        } else {
            verifyNoInteractions(mockStoreService);
        }
    }

    @Test
    public void testPerformInTransaction_valid() {
        final User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Aliquot", null);
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware sourceLw = EntityFactory.getTube();
        Work work = new Work(50, "SGP50", null, null, null, null, Work.Status.active);
        OperationResult result = new OperationResult(List.of(), List.of());

        doReturn(opType).when(service).loadOpType(any(), any());
        doReturn(sourceLw).when(service).loadSourceLabware(any(), any());
        doReturn(lt).when(service).loadDestLabwareType(any(), any());
        doReturn(work).when(mockWorkService).validateUsableWork(any(), any());
        doNothing().when(service).validateRequest(any(), any(), any(), any());
        doReturn(result).when(service).execute(any(), anyInt(), any(), any(), any(), any());

        AliquotRequest request = new AliquotRequest(opType.getName(), sourceLw.getBarcode(), lt.getName(),
                3, work.getWorkNumber());
        assertSame(result, service.performInTransaction(user, request));

        verify(service).loadOpType(anyCollection(), eq(request.getOperationType()));
        verify(service).loadSourceLabware(anyCollection(), eq(request.getBarcode()));
        verify(service).loadDestLabwareType(anyCollection(), eq(request.getLabwareType()));
        verify(mockWorkService).validateUsableWork(anyCollection(), eq(request.getWorkNumber()));
        verify(service).validateRequest(anyCollection(), same(request.getNumLabware()), same(opType), same(sourceLw));
        verify(service).execute(same(user), eq((int) request.getNumLabware()), same(opType), same(sourceLw),
                same(lt), same(work));
    }

    @Test
    public void testPerformInTransaction_invalid() {
        final User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Aliquot", null);
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware sourceLw = EntityFactory.getTube();
        AliquotRequest request = new AliquotRequest(opType.getName(), sourceLw.getBarcode(), lt.getName(),
                -5, "SGPF");

        doAnswer(addProblem("Bad op", opType)).when(service).loadOpType(any(), any());
        doAnswer(addProblem("Bad lw", sourceLw)).when(service).loadSourceLabware(any(), any());
        doAnswer(addProblem("Bad lt", lt)).when(service).loadDestLabwareType(any(), any());
        doAnswer(addProblem("Bad work", null)).when(mockWorkService).validateUsableWork(any(), any());
        doAnswer(addProblem("Bad request", null)).when(service).validateRequest(any(), any(), any(), any());

        ValidationException ex = assertThrows(ValidationException.class, () -> service.performInTransaction(user, request));
        //noinspection unchecked
        assertThat((Collection<Object>) ex.getProblems()).containsExactlyInAnyOrder(
                "Bad op", "Bad lw", "Bad lt", "Bad work", "Bad request"
        );

        verify(service).loadOpType(anyCollection(), eq(request.getOperationType()));
        verify(service).loadSourceLabware(anyCollection(), eq(request.getBarcode()));
        verify(service).loadDestLabwareType(anyCollection(), eq(request.getLabwareType()));
        verify(mockWorkService).validateUsableWork(anyCollection(), eq(request.getWorkNumber()));
        verify(service).validateRequest(anyCollection(), same(request.getNumLabware()), same(opType), same(sourceLw));
        verify(service, never()).execute(any(), anyInt(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "Aliquot, true,",
            "Foozle, false, Unknown operation type: \"Foozle\"",
            ", false, No operation type specified.",
            "'', false, No operation type specified.",
    })
    public void testLoadOpType(String name, boolean exists, String expectedProblem) {
        OperationType opType = (exists ? EntityFactory.makeOperationType(name, null) : null);
        if (name!=null) {
            when(mockOpTypeRepo.findByName(name)).thenReturn(Optional.ofNullable(opType));
        }
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(opType, service.loadOpType(problems, name));
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "STAN-01, true,,",
            "STAN-404, false, Not found.,",
            "STAN-01, true, Bad labware.,",
            ",false,,No source barcode specified.",
            "'',false,,No source barcode specified.",
    })
    public void testLoadSourceLabware(String barcode, boolean exists, String valError, String expectedProblem) {
        Labware lw;
        if (exists) {
            lw = EntityFactory.getTube();
        } else {
            lw = null;
        }
        if (expectedProblem==null) {
            expectedProblem = valError;
        }
        List<Labware> lws = (lw==null) ? List.of() : List.of(lw);
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        when(val.loadLabware(any(), any())).thenReturn(lws);
        when(val.getErrors()).thenReturn(valError==null ? List.of() : List.of(valError));

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(lw, service.loadSourceLabware(problems, barcode));
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
        if (expectedProblem==null || valError!=null) {
            verify(mockLwValFactory).getValidator();
            verify(val).loadLabware(mockLwRepo, List.of(barcode));
            verify(val).setSingleSample(true);
            verify(val).validateSources();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "lt1, true,",
            "lt4, false, Unknown labware type: \"lt4\"",
            ", false, No destination labware type specified.",
            "'', false, No destination labware type specified.",
    })
    public void testLoadDestLabwareType(String name, boolean exists, String expectedProblem) {
        LabwareType lt;
        if (exists) {
            lt = EntityFactory.makeLabwareType(1,1);
            lt.setName(name);
        } else {
            lt = null;
        }
        if (name!=null && !name.isEmpty()) {
            when(mockLwTypeRepo.findByName(name)).thenReturn(Optional.ofNullable(lt));
        }

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(lt, service.loadDestLabwareType(problems, name));
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    @ParameterizedTest
    @MethodSource("validateRequestArgs")
    public void testValidateRequest(Integer numLabware, OperationType opType, Labware sourceLw,
                                    List<String> expectedProblems) {
        final List<String> problems = new ArrayList<>(expectedProblems.size());
        service.validateRequest(problems, numLabware, opType, sourceLw);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateRequestArgs() {
        OperationType aliquotOp =  EntityFactory.makeOperationType("Aliquot", null);
        OperationType inPlaceOp = EntityFactory.makeOperationType("Bloop", null, OperationTypeFlag.IN_PLACE);
        OperationType analysisOp = EntityFactory.makeOperationType("Floop", null, OperationTypeFlag.ANALYSIS);
        OperationType sectionOp = EntityFactory.makeOperationType("Section", null, OperationTypeFlag.SOURCE_IS_BLOCK);

        Sample sample = new Sample(50, null, EntityFactory.getTissue(), EntityFactory.getBioState());
        Labware block = EntityFactory.makeBlock(sample);
        Labware section = EntityFactory.getTube();

        return Arrays.stream(new Object[][]{
                {3, aliquotOp, block},
                {1, aliquotOp, section},
                {null, aliquotOp, section, "Number of labware not specified."},
                {0, aliquotOp, section, "Number of labware must be greater than zero."},
                {-1, aliquotOp, section, "Number of labware must be greater than zero."},
                {1, null, null},
                {2, aliquotOp, null},
                {1, null, section},
                {2, sectionOp, block},
                {3, sectionOp, null},
                {3, sectionOp, section, "The source must be a block for operation type Section."},
                {2, inPlaceOp, section, "Operation type Bloop cannot be used for aliquoting."},
                {1, analysisOp, section, "Operation type Floop cannot be used for aliquoting."},
                {null, inPlaceOp, section, "Number of labware not specified.", "Operation type Bloop cannot be used for aliquoting."},
        }).map(arr -> {
            List<String> expectedProblems = IntStream.range(3, arr.length)
                    .mapToObj(i -> (String) arr[i])
                    .collect(toList());
            return Arguments.of(arr[0], arr[1], arr[2], expectedProblems);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "true,true,false,0",
            "true,true,false,1",
            "true,true,true,0",
            "true,false,false,0",
            "false,true,true,0",
            "false,false,true,1",
    })
    public void testExecute(boolean discard, boolean hasWork, boolean setBioState, int sourceSlotIndex) {
        final int numLabware = 2;
        LabwareType destLwType = EntityFactory.getTubeType();
        Labware sourceLw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(2,1));
        List<Labware> destLw = List.of(
                EntityFactory.makeEmptyLabware(destLwType),
                EntityFactory.makeEmptyLabware(destLwType)
        );
        OperationType opType;
        BioState bs = (setBioState ? new BioState(300, "Bananas") : null);
        if (discard) {
            opType = EntityFactory.makeOperationType("Aliquot", bs, OperationTypeFlag.DISCARD_SOURCE);
            when(mockLwRepo.save(any())).then(Matchers.returnArgument());
        } else {
            opType = EntityFactory.makeOperationType("Aliquot", bs);
        }
        Work work = (hasWork ?
                    new Work(50, "SGP50", null, null, null, null, Work.Status.active)
                    : null);
        User user = EntityFactory.getUser();
        Sample srcSample = EntityFactory.getSample();
        Slot srcSlot = sourceLw.getSlots().get(sourceSlotIndex);
        srcSlot.getSamples().add(srcSample);
        final Integer newSampleId = 600;
        if (bs!=null) {
            when(mockSampleRepo.save(any())).then(invocation -> {
                Sample sample = invocation.getArgument(0);
                assertNull(sample.getId());
                sample.setId(newSampleId);
                return sample;
            });
        }
        when(mockLwService.create(destLwType, numLabware)).thenReturn(destLw);
        doNothing().when(service).updateDestinations(any(), any());
        List<Operation> ops = IntStream.range(0, destLw.size())
                .mapToObj(i -> new Operation(50+i, opType, null, null, user))
                .collect(toList());
        doReturn(ops.get(0), ops.get(1)).when(service).createOperation(any(), any(), any(), any(), any(), any());

        OperationResult result = service.execute(user, numLabware, opType, sourceLw, destLwType, work);

        assertEquals(destLw, result.getLabware());
        assertEquals(ops, result.getOperations());

        verify(mockLwService).create(destLwType, numLabware);
        assertEquals(discard, sourceLw.isDiscarded());
        if (discard) {
            verify(mockLwRepo).save(sourceLw);
        } else {
            verifyNoInteractions(mockLwRepo);
        }
        Sample dstSample;
        if (bs==null) {
            dstSample = srcSample;
            verifyNoInteractions(mockSampleRepo);
        } else {
            ArgumentCaptor<Sample> sampleCaptor = ArgumentCaptor.forClass(Sample.class);
            verify(mockSampleRepo).save(sampleCaptor.capture());
            dstSample = sampleCaptor.getValue();
            assertEquals(newSampleId, dstSample.getId());
            assertEquals(srcSample.getTissue(), dstSample.getTissue());
            assertEquals(srcSample.getSection(), dstSample.getSection());
            assertEquals(bs, dstSample.getBioState());
        }
        verify(service).updateDestinations(destLw, dstSample);
        verify(service, times(2)).createOperation(any(), any(), any(), any(), any(), any());
        for (Labware lw : destLw) {
            verify(service).createOperation(user, opType, srcSample, srcSlot, dstSample, lw.getFirstSlot());
        }
        if (work==null) {
            verifyNoInteractions(mockWorkService);
        } else {
            verify(mockWorkService).link(work, ops);
        }
    }

    @Test
    public void testUpdateDestinations() {
        LabwareType lt = EntityFactory.makeLabwareType(2,1);
        List<Labware> labware = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lt))
                .collect(toList());
        Sample sample = EntityFactory.getSample();

        service.updateDestinations(labware, sample);

        List<Slot> slots = new ArrayList<>(labware.size());
        for (Labware lw : labware) {
            final Slot slot = lw.getFirstSlot();
            assertThat(slot.getSamples()).hasSize(1).contains(sample);
            slots.add(slot);
            assertThat(lw.getSlots().get(1).getSamples()).isEmpty();
        }
        verify(mockSlotRepo).saveAll(slots);
    }

    @Test
    public void testCreateOperation() {
        Slot source = EntityFactory.getTube().getFirstSlot();
        Slot dest = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType()).getFirstSlot();
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Aliquot", null);
        Sample srcSam = EntityFactory.getSample();
        Sample dstSam = new Sample(srcSam.getId()+1, srcSam.getSection(), srcSam.getTissue(), srcSam.getBioState());

        Operation op = new Operation(500, opType, null, null, user);
        when(mockOpService.createOperation(any(), any(), any(), any())).thenReturn(op);

        assertSame(op, service.createOperation(user, opType, srcSam, source, dstSam, dest));

        Action expectedAction = new Action(null, null, source, dest, dstSam, srcSam);

        verify(mockOpService).createOperation(opType, user, List.of(expectedAction), null);
    }
}
