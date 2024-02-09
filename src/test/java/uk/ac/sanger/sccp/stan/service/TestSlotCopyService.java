package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.*;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link SlotCopyServiceImp}
 * @author dr6
 */
public class TestSlotCopyService {
    @Mock
    LabwareRepo mockLwRepo;
    @Mock
    SampleRepo mockSampleRepo;
    @Mock
    SlotRepo mockSlotRepo;
    @Mock
    LabwareNoteRepo mockLwNoteRepo;
    @Mock
    SlotCopyValidationService mockValService;
    @Mock
    LabwareService mockLwService;
    @Mock
    OperationService mockOpService;
    @Mock
    StoreService mockStoreService;
    @Mock
    WorkService mockWorkService;
    @Mock
    EntityManager mockEntityManager;
    @Mock
    Transactor mockTransactor;

    private SlotCopyServiceImp service;
    private OperationType opType;
    private BioState tissue, cdna;
    private User user;
    private LabwareType plateType;
    private LabwareType slideType;
    private List<Sample> sourceSamples;
    private List<Labware> sourceLabware;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        tissue = new BioState(1, "Tissue");
        cdna = new BioState(3, "cDNA");
        opType = EntityFactory.makeOperationType("cDNA", cdna, OperationTypeFlag.MARK_SOURCE_USED);
        plateType = EntityFactory.makeLabwareType(8, 12);
        plateType.setName("platetype");
        slideType = EntityFactory.makeLabwareType(4, 1);
        user = EntityFactory.getUser();

        service = spy(new SlotCopyServiceImp(mockLwRepo, mockSampleRepo, mockSlotRepo, mockLwNoteRepo,
                mockValService, mockLwService, mockOpService, mockStoreService, mockWorkService,
                mockEntityManager, mockTransactor));
    }

    @AfterEach
    void teardown() throws Exception {
        mocking.close();
    }

    private List<Sample> makeSourceSamples(BioState bioState) {
        if (sourceSamples==null) {
            Tissue tissue = EntityFactory.getTissue();
            sourceSamples = IntStream.of(1, 2, 3)
                    .mapToObj(i -> new Sample(10 + i, i, tissue, bioState))
                    .collect(toList());
        }
        return sourceSamples;
    }

    private List<Labware> makeSourceLabware() {
        if (sourceLabware==null) {
            makeSourceSamples(tissue);
            sourceLabware = List.of(
                    EntityFactory.makeLabware(slideType, sourceSamples.get(0), sourceSamples.get(1)),
                    EntityFactory.makeLabware(slideType, sourceSamples.get(1), sourceSamples.get(2))
            );
            sourceLabware.get(0).setBarcode("STAN-001");
            sourceLabware.get(1).setBarcode("STAN-002");
        }
        return sourceLabware;
    }

    private UCMap<Labware> makeLabwareMap() {
        return UCMap.from(makeSourceLabware(), Labware::getBarcode);
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testPerform(boolean valid) {
        SlotCopyRequest request = new SlotCopyRequest("op", "thing", List.of(), null, null);
        ValidationException ex = valid ? null : new ValidationException("Bad", List.of());
        Operation op = new Operation(200, opType, null, null, null);
        OperationResult result = valid ? new OperationResult(List.of(op), List.of()) : null;
        Matchers.mockTransactor(mockTransactor);
            doAnswer(invocation -> {
                if (valid) {
                    Set<String> bcs = invocation.getArgument(2);
                    bcs.add("Alpha");
                    bcs.add("Beta");
                    return result;
                }
                throw ex;

            }).when(service).performInsideTransaction(any(), any(), any());
        if (valid) {
            assertSame(result, service.perform(user, request));
        } else {
            assertSame(ex, assertThrows(ValidationException.class, () -> service.perform(user, request)));
        }
        verify(service).performInsideTransaction(same(user), same(request), notNull());
        if (valid) {
            verify(mockStoreService).discardStorage(user, Set.of("Alpha", "Beta"));
        } else {
            verifyNoInteractions(mockStoreService);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testPerformInsideTransaction(boolean valid) {
        List<SlotCopyContent> contents = List.of(new SlotCopyContent("SOURCE1", new Address(1, 2), new Address(3, 4)));
        Work work = new Work(50, "SGP5000", null, null, null, null, null, Work.Status.active);
        when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        SlotCopyRequest request = new SlotCopyRequest(opType.getName(), plateType.getName(), contents, work.getWorkNumber(), "pbc");
        SlotCopyValidationService.Data data = new SlotCopyValidationService.Data(request);
        if (!valid) {
            data.problems.add("Validation problem.");
        }
        when(mockValService.validateRequest(user, request)).thenReturn(data);
        OperationResult opResult = new OperationResult(List.of(), List.of());
        doReturn(opResult).when(service).record(any(), any(), any());
        final Set<String> barcodesToUnstore = Set.of("bananas");
        if (valid) {
            assertSame(opResult, service.performInsideTransaction(user, request, barcodesToUnstore));
        } else {
            Matchers.assertValidationException(() -> service.performInsideTransaction(user, request, barcodesToUnstore),
                    List.of("Validation problem."));
        }
        verify(mockValService).validateRequest(user, request);
        if (valid) {
            verify(service).record(user, data, barcodesToUnstore);
        } else {
            verify(service, never()).record(any(), any(), any());
        }
    }

    @Test
    public void testExecuteOps() {
        final Address A1 = new Address(1,1);
        User user = EntityFactory.getUser();
        List<SlotCopyDestination> dests = List.of(
                new SlotCopyDestination("lt1", "pb1", SlideCosting.SGP, "1234567", "777777", List.of(new SlotCopyContent("STAN-0", A1, A1)), "bs1"),
                new SlotCopyDestination("lt2", null, SlideCosting.Faculty, null, null, List.of(new SlotCopyContent("STAN-1", A1, A1)), null),
                new SlotCopyDestination(null, null, null, null, null, List.of(new SlotCopyContent("STAN-2", A1, A1)), "bs2")
        );
        OperationType opType = EntityFactory.makeOperationType("optype", null);
        final LabwareType lt1 = EntityFactory.makeLabwareType(1, 1, "lt1");
        final LabwareType lt2 = EntityFactory.makeLabwareType(1, 2, "lt2");
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, lt1, lt2);
        Labware lw1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        Labware lw2 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        lw1.setBarcode("STAN-0");
        lw2.setBarcode("STAN-1");
        Work work = new Work(50, "SGP50", null, null, null, null, null, null);
        UCMap<Labware> sources = UCMap.from(Labware::getBarcode, lw1, lw2);
        final BioState bs = new BioState(10, "bs1");
        UCMap<BioState> bsMap = UCMap.from(BioState::getName, bs);

        Operation op1 = new Operation();
        op1.setId(21);
        Operation op2 = new Operation();
        op2.setId(22);
        Operation op3 = new Operation();
        op3.setId(23);
        Labware newLw1 = EntityFactory.makeEmptyLabware(lt1);
        Labware newLw2 = EntityFactory.makeEmptyLabware(lt2);

        Labware dest1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType(), "STAN-D");

        UCMap<Labware> existingDests = UCMap.from(Labware::getBarcode, dest1);

        dests.get(2).setBarcode(dest1.getBarcode());

        doReturn(new OperationResult(List.of(op1), List.of(newLw1)),
                new OperationResult(List.of(op2), List.of(newLw2)),
                new OperationResult(List.of(op3), List.of(dest1)))
                .when(service).executeOp(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        OperationResult result = service.executeOps(user, dests, opType, lwTypes, bsMap, sources, work, existingDests);
        assertThat(result.getOperations()).containsExactly(op1, op2, op3);
        assertThat(result.getLabware()).containsExactly(newLw1, newLw2, dest1);

        verify(service).executeOp(user, dests.get(0).getContents(), opType, lt1, "pb1", sources, SlideCosting.SGP, "1234567", "777777", bs, null);
        verify(service).executeOp(user, dests.get(1).getContents(), opType, lt2, null, sources, SlideCosting.Faculty, null, null, null, null);
        verify(service).executeOp(user, dests.get(2).getContents(), opType, null, null, sources, null, null, null, null, dest1);

        verify(mockWorkService).link(work, result.getOperations());
    }

    @ParameterizedTest
    @CsvSource({"false,false,false",
            "false,true,false",
            "false,false,true",
            "false,true,true",
            "true,false,false",
            "true,true,true",
    })
    public void testExecuteOp(boolean existingDest, boolean bsInRequest, boolean bsInOpType) {
        final User user = EntityFactory.getUser();
        final BioState rbs = bsInRequest ? new BioState(5, "requestbs") : null;
        final BioState obs = bsInOpType ? new BioState(6, "opbs") : null;
        final BioState dbs = (existingDest && !bsInRequest ? new BioState(7, "dbs") : null);
        List<SlotCopyContent> contents = List.of(new SlotCopyContent());
        final SlideCosting costing = SlideCosting.SGP;
        final String lotNumber = "1234567";
        final String probeLotNumber = "777777";
        LabwareType lt = EntityFactory.getTubeType();
        UCMap<Labware> sourceMap = UCMap.from(Labware::getBarcode, EntityFactory.getTube());
        Labware destLw = EntityFactory.makeEmptyLabware(lt);
        if (!existingDest) {
            when(mockLwService.create(any(), any(), any())).thenReturn(destLw);
        } else if (!bsInRequest) {
            doReturn(dbs).when(service).findBioStateInLabware(destLw);
        }
        final String preBarcode = "pb1";
        OperationType opType = EntityFactory.makeOperationType("optype1", obs);
        Map<Integer, Sample> oldSampleIdToNewSample = Map.of(500, EntityFactory.getSample());
        doReturn(oldSampleIdToNewSample).when(service).createSamples(any(), any(), any());
        Labware filledLw = EntityFactory.makeLabware(lt, EntityFactory.getSample());
        doReturn(filledLw).when(service).fillLabware(any(), any(), any(), any());
        Operation op = new Operation();
        op.setId(50);
        doReturn(op).when(service).createOperation(any(), any(), any(), any(), any(), any());

        OperationResult opres = service.executeOp(user, contents, opType, lt, preBarcode, sourceMap, costing, lotNumber, probeLotNumber, rbs, existingDest ? destLw : null);
        assertThat(opres.getLabware()).containsExactly(filledLw);
        assertThat(opres.getOperations()).containsExactly(op);

        if (existingDest) {
            verifyNoInteractions(mockLwService);
        } else {
            verify(mockLwService).create(lt, preBarcode, preBarcode);
        }
        verify(service).createSamples(contents, sourceMap, dbs!=null ? dbs : bsInRequest ? rbs : obs);
        if (dbs==null) {
            verify(service, never()).findBioStateInLabware(any());
        } else {
            verify(service).findBioStateInLabware(destLw);
        }
        verify(service).fillLabware(destLw, contents, sourceMap, oldSampleIdToNewSample);
        verify(service).createOperation(user, contents, opType, sourceMap, filledLw, oldSampleIdToNewSample);
        verify(mockLwNoteRepo).save(new LabwareNote(null, filledLw.getId(), op.getId(), "costing", costing.name()));
        verify(mockLwNoteRepo).save(new LabwareNote(null, filledLw.getId(), op.getId(), "lot", lotNumber));
        verify(mockLwNoteRepo).save(new LabwareNote(null, filledLw.getId(), op.getId(), "probe lot", probeLotNumber));
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2})
    public void testFindBioStateInLabware(int numBs) {
        Labware lw;
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        BioState expectedBs;
        switch (numBs) {
            case 0 -> {
                lw = EntityFactory.makeEmptyLabware(lt);
                expectedBs = null;
            }
            case 1 -> {
                expectedBs = EntityFactory.getBioState();
                Tissue tissue = EntityFactory.getTissue();
                Sample[] samples = IntStream.range(100,102)
                        .mapToObj(i -> new Sample(i, null, tissue, expectedBs))
                        .toArray(Sample[]::new);
                lw = EntityFactory.makeLabware(lt, samples);
            }
            default -> {
                expectedBs = null;
                Tissue tissue = EntityFactory.getTissue();
                BioState[] bss = IntStream.rangeClosed(1,2)
                        .mapToObj(i -> new BioState(i, "bs"+i))
                        .toArray(BioState[]::new);
                Sample[] samples = IntStream.range(0, bss.length)
                        .mapToObj(i -> new Sample(100+i, null, tissue, bss[i]))
                        .toArray(Sample[]::new);
                lw = EntityFactory.makeLabware(lt, samples);
            }
        }
        assertSame(expectedBs, service.findBioStateInLabware(lw));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCreateSamples(boolean changeBioState) {
        BioState newBioState = changeBioState ? cdna : null;
        Tissue tissue1 = EntityFactory.getTissue();
        Tissue tissue2 = EntityFactory.makeTissue(tissue1.getDonor(), tissue1.getSpatialLocation());
        Sample tissueSample = new Sample(1, 1, tissue1, tissue);
        Sample cdnaSample = new Sample(2, 2, tissue2, cdna);
        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);
        final Address C1 = new Address(3,1);

        List<Labware> labware = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(slideType))
                .collect(toList());
        labware.get(0).getFirstSlot().getSamples().addAll(List.of(tissueSample, cdnaSample));
        labware.get(0).getSlot(B1).getSamples().add(tissueSample);
        labware.get(1).getFirstSlot().getSamples().add(cdnaSample);
        UCMap<Labware> lwMap = UCMap.from(labware, Labware::getBarcode);
        List<SlotCopyContent> contents = List.of(
                new SlotCopyContent(labware.get(0).getBarcode(), A1, A1),
                new SlotCopyContent(labware.get(0).getBarcode(), B1, B1),
                new SlotCopyContent(labware.get(1).getBarcode(), A1, C1)
        );
        int[] idCounter = {500};
        List<Sample> createdSamples = new ArrayList<>();
        when(mockSampleRepo.save(any())).then(invocation -> {
            Sample sam = invocation.getArgument(0);
            assertNull(sam.getId());
            sam.setId(++idCounter[0]);
            createdSamples.add(sam);
            return sam;
        });

        Map<Integer, Sample> result = service.createSamples(contents, lwMap, newBioState);

        assertEquals(2, result.size());
        if (newBioState==null) {
            assertThat(createdSamples).isEmpty();
            assertEquals(tissueSample, result.get(tissueSample.getId()));
        } else {
            assertEquals(1, createdSamples.size());
            Sample sam = createdSamples.getFirst();
            assertEquals(tissueSample.getTissue(), sam.getTissue());
            assertEquals(newBioState, sam.getBioState());
            assertEquals(tissueSample.getSection(), sam.getSection());
            assertEquals(sam, result.get(tissueSample.getId()));
        }

        assertEquals(cdnaSample, result.get(cdnaSample.getId()));
    }


    @Test
    public void testFillLabware() {
        UCMap<Labware> lwMap = makeLabwareMap();
        Map<Integer, Sample> sampleMap = makeSourceSamples(cdna).stream()
                .collect(BasicUtils.inMap(Sample::getId));
        Labware lw = EntityFactory.makeEmptyLabware(plateType);
        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);
        List<SlotCopyContent> contents = List.of(
                new SlotCopyContent("STAN-001", A1, A1),
                new SlotCopyContent("STAN-001", B1, B1),
                new SlotCopyContent("STAN-002", A1, A1)
        );

        assertSame(lw, service.fillLabware(lw, contents, lwMap, sampleMap));
        verify(mockSlotRepo).saveAll(List.of(lw.getSlot(A1), lw.getSlot(B1)));
        verify(mockEntityManager).refresh(lw);

        assertThat(lw.getSlot(A1).getSamples()).containsExactlyInAnyOrder(
                sourceSamples.get(0), sourceSamples.get(1)
        );
        assertThat(lw.getSlot(B1).getSamples()).containsExactlyInAnyOrder(
                sourceSamples.get(1)
        );
    }

    @Test
    public void testCreateOperation() {
        UCMap<Labware> lwMap = makeLabwareMap();
        Sample newSample = new Sample(500, 3, EntityFactory.getTissue(), cdna);
        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);
        final Address A2 = new Address(1,2);
        final Address B2 = new Address(2,2);

        Map<Integer, Sample> sampleMap = Map.of(
                sourceSamples.get(0).getId(), sourceSamples.get(0),
                sourceSamples.get(1).getId(), sourceSamples.get(1),
                sourceSamples.get(2).getId(), newSample
        );
        List<SlotCopyContent> contents = List.of(
                new SlotCopyContent("STAN-001", A1, A2),
                new SlotCopyContent("STAN-002", B1, B2)
        );
        Labware lw1 = sourceLabware.get(0);
        Labware lw2 = sourceLabware.get(1);
        Labware destLw = EntityFactory.makeEmptyLabware(plateType);

        Operation op = new Operation(10, opType, null, null, null);
        when(mockOpService.createOperation(any(), any(), any(), isNull())).thenReturn(op);

        assertSame(op, service.createOperation(user, contents, opType, lwMap, destLw, sampleMap));

        List<Action> expectedActions = List.of(
                new Action(null, null, lw1.getSlot(A1), destLw.getSlot(A2), sourceSamples.get(0), sourceSamples.get(0)),
                new Action(null, null, lw2.getSlot(B1), destLw.getSlot(B2), newSample, sourceSamples.get(2))
        );
        verify(mockOpService).createOperation(opType, user, expectedActions, null);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings={"discarded", "used"})
    public void testUpdateSources(Labware.State defaultState) {
        List<SlotCopySource> sourceStates = List.of(
                new SlotCopySource("STAN-A", Labware.State.active),
                new SlotCopySource("STAN-U", Labware.State.used),
                new SlotCopySource("STAN-D", Labware.State.discarded)
        );
        final LabwareType lt = EntityFactory.getTubeType();
        final Sample sample = EntityFactory.getSample();
        List<Labware> labware = "AUD0".chars().mapToObj(ch -> {
            String bc = "STAN-"+Character.toString(ch);
            Labware lw = EntityFactory.makeLabware(lt, sample);
            lw.setBarcode(bc);
            return lw;
        }).collect(toList());
        Labware lwA = labware.get(0), lwU = labware.get(1), lwD = labware.get(2), lw0 = labware.get(3);
        final Set<String> barcodesToUnstore = new HashSet<>(2);
        service.updateSources(sourceStates, labware, defaultState, barcodesToUnstore);
        //noinspection unchecked
        ArgumentCaptor<Collection<Labware>> lwCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mockLwRepo).saveAll(lwCaptor.capture());
        Collection<Labware> saved = lwCaptor.getValue();
        assertSame(Labware.State.active, lwA.getState());
        assertSame(Labware.State.used, lwU.getState());
        assertSame(Labware.State.discarded, lwD.getState());
        if (defaultState==null) {
            assertSame(Labware.State.active, lw0.getState());
            assertThat(saved).containsExactlyInAnyOrder(lwU, lwD);
        } else {
            assertSame(defaultState, lw0.getState());
            assertThat(saved).containsExactlyInAnyOrder(lwU, lwD, lw0);
        }
        if (defaultState== Labware.State.discarded) {
            assertThat(barcodesToUnstore).containsExactlyInAnyOrder("STAN-D", "STAN-0");
        } else {
            assertThat(barcodesToUnstore).containsExactly("STAN-D");
        }
    }
}
