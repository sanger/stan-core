package uk.ac.sanger.sccp.stan.service.extract;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ExtractRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ExtractServiceImp}
 * @author dr6
 */
public class TestExtractService {
    private Transactor mockTransactor;
    private LabwareValidatorFactory mockLabwareValidatorFactory;
    private LabwareService mockLwService;
    private OperationService mockOpService;
    private StoreService mockStoreService;
    private WorkService mockWorkService;
    private LabwareRepo mockLwRepo;
    private LabwareTypeRepo mockLtRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private SampleRepo mockSampleRepo;
    private SlotRepo mockSlotRepo;

    private ExtractServiceImp service;

    private OperationType opType;
    private LabwareType lwType;
    private BioState rnaBioState;

    private static <E extends Exception> void assertThrowsMsg(Class<E> exceptionClass, String message, Executable exec) {
        assertThat(Assertions.assertThrows(exceptionClass, exec)).hasMessage(message);
    }

    @BeforeEach
    void setup() {
        mockTransactor = mock(Transactor.class);
        mockLabwareValidatorFactory = mock(LabwareValidatorFactory.class);
        mockLwService = mock(LabwareService.class);
        mockOpService = mock(OperationService.class);
        mockStoreService = mock(StoreService.class);
        mockWorkService = mock(WorkService.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockLtRepo = mock(LabwareTypeRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockSlotRepo = mock(SlotRepo.class);

        rnaBioState = new BioState(2, "RNA");
        opType = EntityFactory.makeOperationType("Extract", rnaBioState, OperationTypeFlag.DISCARD_SOURCE);
        lwType = new LabwareType(6, "lwtype", 1, 1, EntityFactory.getLabelType(), false);

        service = spy(new ExtractServiceImp(mockTransactor, mockLabwareValidatorFactory, mockLwService, mockOpService,
                mockStoreService, mockWorkService, mockLwRepo, mockLtRepo, mockOpTypeRepo, mockSampleRepo, mockSlotRepo));
    }

    @Test
    public void testExtractAndUnstore() {
        User user = EntityFactory.getUser();
        ExtractRequest request = new ExtractRequest(List.of("STAN-A1"), "lt", "SGP5000");
        OperationResult result = new OperationResult();
        doReturn(result).when(service).transactExtract(any(), any());

        assertSame(result, service.extractAndUnstore(user, request));

        InOrder inOrder = inOrder(service, mockStoreService);
        inOrder.verify(service).transactExtract(user, request);
        inOrder.verify(mockStoreService).discardStorage(user, request.getBarcodes());
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testTransactExtract(boolean successful) {
        User user = EntityFactory.getUser();
        ExtractRequest request = new ExtractRequest(List.of("STAN-A1"), "lt", "SGP5000");
        OperationResult opResult;
        IllegalArgumentException exception;
        if (successful) {
            opResult = new OperationResult();
            exception = null;
            doReturn(opResult).when(service).extract(any(), any());
        } else {
            opResult = null;
            exception = new IllegalArgumentException();
            doThrow(exception).when(service).extract(any(), any());
        }
        Matchers.mockTransactor(mockTransactor);

        if (successful) {
            assertSame(opResult, service.transactExtract(user, request));
        } else {
            assertSame(exception, assertThrows(exception.getClass(), () -> service.transactExtract(user, request)));
        }

        verify(service).extract(user, request);
        verify(mockTransactor).transact(anyString(), any());
    }

    @Test
    public void testExtract() {
        String ltName = lwType.getName();
        User user = EntityFactory.getUser();
        when(mockLtRepo.getByName(ltName)).thenReturn(lwType);
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);

        Labware src = EntityFactory.getTube();
        Labware dst = EntityFactory.makeEmptyLabware(lwType);

        List<Labware> sources = List.of(src);
        List<String> bcs = List.of(src.getBarcode());
        List<Operation> ops = List.of(EntityFactory.makeOpForLabware(opType, sources, List.of(dst), user));
        Work work = new Work(500, "SGP5000", new WorkType(1, "Bananas"), null, null, null, null, Work.Status.active);

        when(mockWorkService.getUsableWork(work.getWorkNumber())).thenReturn(work);

        Map<Labware, Labware> lwMap = Map.of(src, dst);
        doReturn(sources).when(service).loadAndValidateLabware(any());
        doReturn(sources).when(service).discardSources(any());
        doReturn(lwMap).when(service).createNewLabware(any(), any());
        Map<Integer, Sample> sampleMap = Map.of(400, src.getFirstSlot().getSamples().get(0));
        doReturn(sampleMap).when(service).createSamples(any(), any());
        doReturn(ops).when(service).createOperations(any(), any(), any(), any());

        assertThrowsMsg(IllegalArgumentException.class, "No barcodes specified.", () -> service.extract(user, new ExtractRequest(List.of(), ltName, "SGP5000")));
        assertThrowsMsg(IllegalArgumentException.class, "No labware type specified.", () -> service.extract(user, new ExtractRequest(bcs, null, "SGP5000")));

        verify(service, never()).loadAndValidateLabware(any());
        verify(service, never()).discardSources(any());
        verify(service, never()).markSourcesUsed(any());
        verify(service, never()).createNewLabware(any(), any());
        verify(service, never()).createSamples(any(), any());
        verify(service, never()).createOperations(any(), any(), any(), any());

        assertEquals(new OperationResult(ops, List.of(dst)), service.extract(user, new ExtractRequest(bcs, ltName, work.getWorkNumber())));

        verify(service).loadAndValidateLabware(bcs);
        verify(service).discardSources(sources);
        verify(service).createNewLabware(lwType, sources);
        verify(service).createSamples(lwMap, rnaBioState);
        verify(service).createOperations(user, opType, lwMap, sampleMap);
        verify(mockWorkService).link(work, ops);
    }

    @Test
    public void testLoadAndValidateLabware() {
        LabwareValidator mockValidator = mock(LabwareValidator.class);
        when(mockLabwareValidatorFactory.getValidator()).thenReturn(mockValidator);

        Labware lw = EntityFactory.makeLabware(lwType);
        List<Labware> labware = List.of(lw);
        List<String> bcs = List.of(lw.getBarcode());
        when(mockValidator.loadLabware(any(), any())).thenReturn(labware);

        assertEquals(labware, service.loadAndValidateLabware(bcs));

        verify(mockValidator).setUniqueRequired(true);
        verify(mockValidator, never()).setSingleSample(true);
        verify(mockValidator).setOneFilledSlotRequired(true);
        verify(mockValidator).loadLabware(mockLwRepo, bcs);
        verify(mockValidator).validateSources();
        verify(mockValidator).throwError(any());
    }

    /**
     * Check that {@link ExtractServiceImp#createNewLabware} produces the right map in the right order.
     */
    @Test
    public void testCreateNewLabware() {
        Labware src2 = EntityFactory.makeEmptyLabware(lwType); // note that the sources are not created in 1,2,3 order
        Labware src1 = EntityFactory.makeEmptyLabware(lwType);
        Labware src3 = EntityFactory.makeEmptyLabware(lwType);
        Labware dst1 = EntityFactory.makeEmptyLabware(lwType);
        Labware dst2 = EntityFactory.makeEmptyLabware(lwType);
        Labware dst3 = EntityFactory.makeEmptyLabware(lwType);
        when(mockLwService.create(any(LabwareType.class))).thenReturn(dst1, dst2, dst3);

        final Map<Labware, Labware> lwMap = service.createNewLabware(lwType, List.of(src1, src2, src3));
        // The map entries must match the order that the sources were passed in.
        assertThat(lwMap).containsExactly(Map.entry(src1, dst1), Map.entry(src2, dst2), Map.entry(src3, dst3));
        verify(mockLwService, times(3)).create(lwType);
    }

    @Test
    public void testDiscardSources() {
        when(mockLwRepo.save(any())).then(Matchers.returnArgument());
        List<Labware> labware = List.of(EntityFactory.makeEmptyLabware(lwType), EntityFactory.makeEmptyLabware(lwType));
        assertEquals(labware, service.discardSources(labware));

        for (Labware lw : labware) {
            assertTrue(lw.isDiscarded());
            verify(mockLwRepo).save(lw);
        }
    }

    @Test
    public void testMarkSourcesUsed() {
        when(mockLwRepo.save(any())).then(Matchers.returnArgument());
        List<Labware> labware = List.of(EntityFactory.makeEmptyLabware(lwType), EntityFactory.makeEmptyLabware(lwType));
        assertEquals(labware, service.markSourcesUsed(labware));

        for (Labware lw : labware) {
            assertTrue(lw.isUsed());
            verify(mockLwRepo).save(lw);
        }
    }

    @Test
    public void testCreateSamples() {
        final List<Sample> saveSampleArgs = new ArrayList<>();
        final int[] sampleIdCounter = {1000};
        when(mockSampleRepo.save(any())).then(invocation -> {
            Sample sample = invocation.getArgument(0);
            saveSampleArgs.add(sample);
            return new Sample(++sampleIdCounter[0], sample.getSection(), sample.getTissue(), sample.getBioState());
        });
        when(mockSlotRepo.save(any())).then(Matchers.returnArgument());
        Tissue tissue = EntityFactory.getTissue();
        BioState tissueBioState = new BioState(1, "Tissue");
        Sample[] sourceSamples = {
                new Sample(1, null, tissue, tissueBioState),
                new Sample(2, 3, tissue, tissueBioState),
                new Sample(3, null, tissue, rnaBioState),
        };

        // Create the sources in a different order from the order we are going to pass them in,
        // to make sure the method orders them correctly
        Labware source2 = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1, 2), null, sourceSamples[1]);

        // source 1: a tissue sample in the first slot
        // source 2: a tissue sample in the SECOND slot
        // source 3: an RNA sample in the first slot
        Labware[] sources = {
                EntityFactory.makeLabware(lwType, sourceSamples[0]),
                source2,
                EntityFactory.makeLabware(lwType, sourceSamples[2]),
        };
        sources[2].getFirstSlot().addSample(sourceSamples[0]);

        Labware[] dests = IntStream.range(0, sources.length)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lwType))
                .toArray(Labware[]::new);

        final Map<Labware, Labware> lwMap = new LinkedHashMap<>(3);
        IntStream.range(0, sources.length)
                .forEach(i -> lwMap.put(sources[i], dests[i]));

        service.createSamples(lwMap, rnaBioState);

        assertThat(saveSampleArgs).containsOnly(
                new Sample(null, sourceSamples[0].getSection(), tissue, rnaBioState),
                new Sample(null, sourceSamples[1].getSection(), tissue, rnaBioState)
        );

        Sample[][] newSamples = Arrays.stream(dests).map(lw -> lw.getFirstSlot().getSamples().toArray(Sample[]::new)).toArray(Sample[][]::new);
        // Each sample has its own non-null sample id
        assertEquals(3, Arrays.stream(newSamples).flatMap(ss -> Arrays.stream(ss).map(Sample::getId)).filter(Objects::nonNull).distinct().count());
        for (int i = 0; i < 2; ++i) {
            Sample sourceSample = sourceSamples[i];
            assertEquals(newSamples[i][0], new Sample(newSamples[i][0].getId(), sourceSample.getSection(), tissue, rnaBioState));
        }
        assertSame(newSamples[2][0], sourceSamples[2]);
        assertSame(newSamples[2][1], newSamples[0][0]);
        for (Labware dest : dests) {
            verify(mockSlotRepo).save(dest.getFirstSlot());
        }
    }

    @Test
    public void testCreateOperations() {
        BioState tissueBioState = new BioState(1, "Tissue");
        Tissue tissue = EntityFactory.getTissue();
        Sample[] srcSamples = IntStream.range(0,2)
                .mapToObj(i -> new Sample(i+1, i+2, tissue, tissueBioState))
                .toArray(Sample[]::new);
        Sample[] dstSamples = Arrays.stream(srcSamples)
                .map(ss -> new Sample(10+ss.getId(), ss.getSection(), tissue, rnaBioState))
                .toArray(Sample[]::new);

        // Create the samples in a different order from how we pass them into the method
        Labware[] srcLabware = new Labware[2];
        srcLabware[1] = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,2), null, srcSamples[1]);
        srcLabware[0] = EntityFactory.makeLabware(lwType, srcSamples[0]);

        Labware[] dstLabware = Arrays.stream(dstSamples)
                .map(sam -> EntityFactory.makeLabware(lwType, sam))
                .toArray(Labware[]::new);

        final Map<Labware, Labware> labwareMap = new LinkedHashMap<>(srcSamples.length);
        IntStream.range(0, srcSamples.length)
                .forEach(i -> labwareMap.put(srcLabware[i], dstLabware[i]));
        final List<Operation> createdOps = new ArrayList<>();
        when(mockOpService.createOperation(any(), any(), any(), any())).then(invocation -> {
            OperationType opType = invocation.getArgument(0);
            User user = invocation.getArgument(1);
            List<Action> actions = invocation.getArgument(2);
            Integer planId = invocation.getArgument(3);
            int opId = 100 + createdOps.size();
            Operation op = new Operation(opId, opType, LocalDateTime.now(), actions, user, planId);
            createdOps.add(op);
            return op;
        });

        final Map<Integer, Sample> sampleMap = IntStream.range(0, srcSamples.length).boxed()
                .collect(toMap(i -> srcSamples[i].getId(), i -> dstSamples[i]));

        final User user = EntityFactory.getUser();
        List<Operation> returnedOps = service.createOperations(user, opType, labwareMap, sampleMap);
        assertEquals(createdOps, returnedOps);

        // make sure the ops are in the order corresponding to the labware
        assertEquals(srcLabware[0].getId(), returnedOps.get(0).getActions().get(0).getSource().getLabwareId());

        for (int i = 0; i < 2; ++i) {
            Operation op = returnedOps.get(i);
            assertEquals(1, op.getActions().size());
            Action action = op.getActions().get(0);
            Slot srcSlot = srcLabware[i].getSlots().get(i); // get slot i from labware i, because of how they're set up
            assertSame(srcSlot, action.getSource());
            assertSame(dstLabware[i].getFirstSlot(), action.getDestination());
            assertSame(srcSamples[i], action.getSourceSample());
            assertSame(dstSamples[i], action.getSample());

            assertSame(user, op.getUser());
            assertSame(opType, op.getOperationType());
            assertNull(op.getPlanOperationId());
        }
    }
}
