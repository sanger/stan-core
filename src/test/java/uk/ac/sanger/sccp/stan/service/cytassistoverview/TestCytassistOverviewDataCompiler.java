package uk.ac.sanger.sccp.stan.service.cytassistoverview;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentAction;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.cytassistoverview.CytassistOverviewDataCompilerImp.CytData;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Posterity;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;
import uk.ac.sanger.sccp.utils.Zip;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.eqCi;
import static uk.ac.sanger.sccp.stan.Matchers.genericCaptor;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/** Test {@link CytassistOverviewDataCompilerImp} */
class TestCytassistOverviewDataCompiler {
    @Mock
    OperationTypeRepo mockOpTypeRepo;
    @Mock
    OperationRepo mockOpRepo;
    @Mock
    LabwareRepo mockLwRepo;
    @Mock
    StainTypeRepo mockStainTypeRepo;
    @Mock
    LabwareProbeRepo mockLwProbeRepo;
    @Mock
    LabwareNoteRepo mockLwNoteRepo;
    @Mock
    MeasurementRepo mockMeasurementRepo;
    @Mock
    ReagentActionRepo mockReagentActionRepo;
    @Mock
    ReagentPlateRepo mockReagentPlateRepo;
    @Mock
    OperationCommentRepo mockOpComRepo;
    @Mock
    ReleaseRepo mockReleaseRepo;
    @Mock
    LabwareFlagRepo mockLwFlagRepo;
    @Mock
    WorkRepo mockWorkRepo;
    @Mock
    Ancestoriser mockAncestoriser;

    @InjectMocks
    CytassistOverviewDataCompilerImp dataCompiler;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        dataCompiler = spy(dataCompiler);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    OperationType mockOpType(String name) {
        OperationType opType = EntityFactory.makeOperationType(name, null);
        when(mockOpTypeRepo.getByName(eqCi(name))).thenReturn(opType);
        return opType;
    }

    @Test
    void testExecute() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        OperationType opType = mockOpType("cytassist");
        Sample[] samples = EntityFactory.makeSamples(2);
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw1 = EntityFactory.makeLabware(lt, samples);
        Labware lw2 = EntityFactory.makeLabware(lt, samples);
        Labware lw3 = EntityFactory.makeLabware(lt, samples);
        User user = EntityFactory.getUser();
        Operation op1 = EntityFactory.makeOpForSlots(opType, lw1.getSlots(), lw2.getSlots(), user);
        Operation op2 = EntityFactory.makeOpForSlots(opType, lw1.getSlots(), lw2.getSlots(), user);
        List<Operation> ops = List.of(op1, op2);
        when(mockOpRepo.findAllByOperationType(opType)).thenReturn(ops);
        Posterity posterity = new Posterity();
        doReturn(posterity).when(dataCompiler).loadPosterity(any());
        Set<Integer> sourceSlotIds = Set.of(lw1.getSlot(A1).getId(), lw1.getSlot(A2).getId());
        Set<Integer> allDestSlotIds = Stream.of(lw2, lw3).flatMap(lw -> lw.getSlots().stream())
                .map(Slot::getId).collect(toSet());
        doReturn(allDestSlotIds).when(dataCompiler).destSlotIds(same(posterity));

        doReturn(null).when(dataCompiler).loadCytLabware(any());
        doNothing().when(dataCompiler).loadSourceCreation(any(), any());
        doNothing().when(dataCompiler).fillCytassistData(any());
        doNothing().when(dataCompiler).loadLp(any());
        doNothing().when(dataCompiler).loadSourceCreation(any(), any());
        doNothing().when(dataCompiler).loadStains(any(), any());
        doNothing().when(dataCompiler).loadImages(any(), any());
        doNothing().when(dataCompiler).loadProbes(any(), any());
        doNothing().when(dataCompiler).loadProbeQC(any(), any());
        doNothing().when(dataCompiler).loadTissueCoverage(any(), any());
        doNothing().when(dataCompiler).loadQPCR(any(), any(), any());
        doNothing().when(dataCompiler).loadAmpCq(any(), any(), any());
        doNothing().when(dataCompiler).loadDualIndex(any(), any(), any());
        doNothing().when(dataCompiler).loadVisiumConcentration(any(), any(), any());
        doNothing().when(dataCompiler).loadLatestLabware(any(), any());
        doNothing().when(dataCompiler).loadFlags(any(), any());
        doNothing().when(dataCompiler).loadWorkNumbers(any());
        doNothing().when(dataCompiler).setUsers(any());

        List<CytassistOverview> result = dataCompiler.execute();

        ArgumentCaptor<List<CytData>> captor = genericCaptor(List.class);
        verify(dataCompiler).loadCytLabware(captor.capture());
        List<CytData> data = captor.getValue();
        assertThat(data.stream().map(d -> d.cytAction))
                .containsExactlyElementsOf(ops.stream()
                        .flatMap(op -> op.getActions().stream()).toList());
        verify(dataCompiler).loadSourceCreation(same(data), eq(sourceSlotIds));
        verify(dataCompiler).loadCytLabware(same(data));
        verify(dataCompiler).fillCytassistData(same(data));
        verify(dataCompiler).loadLp(same(data));
        verify(dataCompiler).loadStains(same(data), eq(sourceSlotIds));
        verify(dataCompiler).loadImages(same(data), eq(sourceSlotIds));
        verify(dataCompiler).loadProbes(same(data), eq(sourceSlotIds));
        verify(dataCompiler).loadProbeQC(same(data), eq(sourceSlotIds));
        verify(dataCompiler).loadTissueCoverage(same(data), eq(sourceSlotIds));
        verify(dataCompiler).loadQPCR(same(data), same(posterity), same(allDestSlotIds));
        verify(dataCompiler).loadAmpCq(same(data), same(posterity), same(allDestSlotIds));
        verify(dataCompiler).loadDualIndex(same(data), same(posterity), same(allDestSlotIds));
        verify(dataCompiler).loadVisiumConcentration(same(data), same(posterity), same(allDestSlotIds));
        verify(dataCompiler).loadLatestLabware(same(data), same(posterity));
        verify(dataCompiler).loadFlags(same(data), same(posterity));
        verify(dataCompiler).loadWorkNumbers(same(data));
        verify(dataCompiler).setUsers(same(data));

        assertThat(result).containsExactlyElementsOf(data.stream().map(d -> d.row).toList());
    }

    @Test
    void testLoadPosterity() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        Sample[] samples = EntityFactory.makeSamples(2);
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw1 = EntityFactory.makeLabware(lt, samples);
        Labware lw2 = EntityFactory.makeLabware(lt, samples);
        Operation op = new Operation();
        User user = EntityFactory.getUser();
        op.setUser(user);

        List<Action> actions = List.of(
                new Action(10, 1, lw1.getSlot(A1), lw2.getSlot(A1), samples[1], samples[0]),
                new Action(11, 1, lw1.getSlot(A2), lw2.getSlot(A2), samples[1], samples[1])
        );
        List<CytData> data = actions.stream()
                .map(a -> new CytData(a, op))
                .toList();
        Posterity posterity = new Posterity();
        when(mockAncestoriser.findPosterity(any())).thenReturn(posterity);
        assertSame(posterity, dataCompiler.loadPosterity(data));
        verify(mockAncestoriser).findPosterity(Set.of(new SlotSample(lw1.getSlot(A1), samples[0]), new SlotSample(lw1.getSlot(A2), samples[1])));
        for (CytData d : data) {
            assertThat(d.users).containsExactly(user);
        }
    }

    @Test
    void testLoadCytLabware() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw1 = EntityFactory.makeLabware(lt, sample, sample);
        Labware lw2 = EntityFactory.makeLabware(lt, sample, sample);
        List<Action> actions = List.of(
                new Action(11, 1, lw1.getSlot(A1), lw1.getSlot(A2), sample, sample),
                new Action(12, 1, lw1.getSlot(A2), lw2.getSlot(A2), sample, sample)
        );
        List<CytData> data = actions.stream()
                .map(a -> new CytData(a, null))
                .toList();
        when(mockLwRepo.findAllByIdIn(any())).thenReturn(List.of(lw1, lw2));
        dataCompiler.loadCytLabware(data);
        verify(mockLwRepo).findAllByIdIn(Set.of(lw1.getId(), lw2.getId()));
        assertSame(lw1, data.get(0).sourceLabware);
        assertSame(lw1, data.get(0).destLabware);
        assertSame(lw1, data.get(1).sourceLabware);
        assertSame(lw2, data.get(1).destLabware);
    }

    @Test
    void testLoadLp() {
        Operation[] ops = IntStream.rangeClosed(1,2).mapToObj(id -> {
            Operation op = new Operation();
            op.setId(id);
            return op;
        }).toArray(Operation[]::new);
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware[] lws = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lt))
                .toArray(Labware[]::new);
        List<CytData> data = Zip.of(Arrays.stream(ops),  Arrays.stream(lws))
                .map((op, lw) -> {
                    CytData d = new CytData(null, op);
                    d.destLabware = lw;
                    return d;
                })
                .toList();
        List<LabwareNote> notes = List.of(
                new LabwareNote(100, lws[0].getId(), ops[0].getId(), "LP number", "7"),
                new LabwareNote(101, lws[0].getId(), ops[0].getId(), "bananas", "8"),
                new LabwareNote(102, lws[1].getId(), ops[1].getId(), "LP number", "9")
        );
        when(mockLwNoteRepo.findAllByOperationIdIn(any())).thenReturn(notes);

        dataCompiler.loadLp(data);

        verify(mockLwNoteRepo).findAllByOperationIdIn(Set.of(ops[0].getId(), ops[1].getId()));
        assertEquals("7", data.get(0).row.getCytassistLp());
        assertEquals("9", data.get(1).row.getCytassistLp());
    }

    @Test
    void testLoadWorkNumbers() {
        Operation[] ops = IntStream.rangeClosed(1,2).mapToObj(id -> {
            Operation op = new Operation();
            op.setId(id);
            return op;
        }).toArray(Operation[]::new);
        List<CytData> data = Arrays.stream(ops).map(op -> new CytData(null, op)).toList();
        Map<Integer, Set<String>> opWorks = Map.of(
                1, Set.of("SGP1"),
                2, Set.of("SGP1", "SGP2")
        );
        when(mockWorkRepo.findWorkNumbersForOpIds(any())).thenReturn(opWorks);
        dataCompiler.loadWorkNumbers(data);
        verify(mockWorkRepo).findWorkNumbersForOpIds(Set.of(1,2));
        assertEquals("SGP1", data.get(0).row.getWorkNumber());
        assertThat(data.get(1).row.getWorkNumber()).isIn("SGP2, SGP1", "SGP1, SGP2");
    }

    @Test
    void testLoadSourceCreation() {
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Sample sample = EntityFactory.getSample();
        User user = EntityFactory.getUser();
        Labware[] lws = IntStream.range(0,3)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);
        Set<Integer> sourceSlotIds = Arrays.stream(lws).map(lw -> lw.getFirstSlot().getId()).collect(toSet());
        OperationType[] opTypes = Stream.of("Register", "Section")
                .map(name -> EntityFactory.makeOperationType(name, null))
                .peek(ot -> when(mockOpTypeRepo.getByName(eqCi(ot.getName()))).thenReturn(ot))
                .toArray(OperationType[]::new);
        Action[] creationActions = {
                new Action(11, 1, lws[0].getFirstSlot(), lws[0].getFirstSlot(), sample, sample),
                new Action(21, 2, lws[1].getFirstSlot(), lws[1].getFirstSlot(), sample, sample),
        };
        Operation[] creationOps = {
                new Operation(1, opTypes[0], LocalDateTime.of(2025,8,26,12,0), List.of(creationActions[0]), user),
                new Operation(2, opTypes[1], LocalDateTime.of(2025,8,27,12,0), List.of(creationActions[1]), user),
        };
        Arrays.stream(creationOps).forEach(op ->
            when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(same(op.getOperationType()), any())).thenReturn(List.of(op))
        );
        Action[] cytActions = {
                new Action(31, 3, lws[0].getFirstSlot(), lws[0].getFirstSlot(), sample, sample),
                new Action(41, 4, lws[1].getFirstSlot(), lws[1].getFirstSlot(), sample, sample),
        };
        List<CytData> cytData = Arrays.stream(cytActions)
                .map(a -> new CytData(a, null))
                .toList();

        dataCompiler.loadSourceCreation(cytData, sourceSlotIds);
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opTypes[0], sourceSlotIds);
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opTypes[1], sourceSlotIds);
        Zip.of(Arrays.stream(creationOps), cytData.stream())
                .forEach((cop, d) -> {
                    assertEquals(cop.getPerformed(), d.row.getSourceLabwareCreated());
                    assertThat(d.users).containsExactly(user);
                });
    }

    @Test
    void testFillCytassistData() {
        LabwareType[] lts = {EntityFactory.makeLabwareType(1,2),
                EntityFactory.makeLabwareType(1,2, "cyt")};
        Sample sample = EntityFactory.getSample();
        User user = EntityFactory.getUser();
        Labware[] lws = IntStream.range(0,4).mapToObj(i -> EntityFactory.makeLabware(lts[i&1], sample)).toArray(Labware[]::new);
        Action[] cytActions = IntStream.range(0,2).mapToObj(i -> {
            Labware lw0 = lws[2*i];
            Labware lw1 = lws[2*i+1];
            Address ad0 = new Address(1, 1+i);
            Address ad1 = new Address(1, 2-i);
            return new Action(10*i+11, i+1, lw0.getSlot(ad0), lw1.getSlot(ad1), sample, sample);
        }).toArray(Action[]::new);
        LocalDateTime[] opTimes = {
                LocalDateTime.of(2025,8,26,12,0),
                LocalDateTime.of(2025,8,27,12,0),
        };
        Operation[] cytOps = Zip.of(Arrays.stream(cytActions), Arrays.stream(opTimes))
                .map((ac, time) -> new Operation(ac.getOperationId(), null, time, List.of(ac), user))
                .toArray(Operation[]::new);
        List<CytData> data = IntStream.range(0,2).mapToObj(i -> {
            Action a = cytActions[i];
            Operation op = cytOps[i];
            Labware lw0 = lws[2*i];
            Labware lw1 = lws[2*i+1];
            CytData d = new CytData(a, op);
            d.sourceLabware = lw0;
            d.destLabware = lw1;
            return d;
        }).toList();
        dataCompiler.fillCytassistData(data);
        CytassistOverview row = data.getFirst().row;
        assertEquals(1, row.getSection());
        assertEquals(lws[0].getBarcode(), row.getSourceBarcode());
        assertEquals(lws[1].getBarcode(), row.getCytassistBarcode());
        assertEquals("A1", row.getSourceSlotAddress());
        assertEquals("A2", row.getCytassistSlotAddress());
        assertEquals(sample.getId(), row.getSampleId());
        assertEquals(sample.getTissue().getExternalName(), row.getSourceExternalName());
        assertEquals(lts[0].getName(), row.getSourceLabwareType());
        assertEquals(lts[1].getName(), row.getCytassistLabwareType());
        assertEquals(opTimes[0], row.getCytassistPerformed());
        row = data.get(1).row;
        assertEquals(1, row.getSection());
        assertEquals(lws[2].getBarcode(), row.getSourceBarcode());
        assertEquals(lws[3].getBarcode(), row.getCytassistBarcode());
        assertEquals("A2", row.getSourceSlotAddress());
        assertEquals("A1", row.getCytassistSlotAddress());
        assertEquals(sample.getId(), row.getSampleId());
        assertEquals(sample.getTissue().getExternalName(), row.getSourceExternalName());
        assertEquals(lts[0].getName(), row.getSourceLabwareType());
        assertEquals(lts[1].getName(), row.getCytassistLabwareType());
        assertEquals(opTimes[1], row.getCytassistPerformed());
    }

    @Test
    void testLoadStains() {
        OperationType ot = EntityFactory.makeOperationType("Stain", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(ot);
        User user = EntityFactory.getUser();
        StainType[] stainTypes = IntStream.rangeClosed(1,3).mapToObj(i -> new StainType(i, "st"+i)).toArray(StainType[]::new);
        LabwareType lt = EntityFactory.makeLabwareType(1,1);
        Labware[] lws = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lt))
                .toArray(Labware[]::new);
        Slot[] slots = Arrays.stream(lws).map(Labware::getFirstSlot).toArray(Slot[]::new);
        Set<Integer> sourceSlotIds = Arrays.stream(slots).map(Slot::getId).collect(toSet());
        Action[] stainActions = {
                new Action(11, 1, slots[0], slots[0], null, null),
                new Action(21, 2, slots[1], slots[1], null, null),
        };
        LocalDateTime[] stainTimes = {LocalDateTime.of(2025,8,26,12,0),
                LocalDateTime.of(2025,8,27,12,0)};

        List<Operation> stainOps = Zip.of(Arrays.stream(stainActions), Arrays.stream(stainTimes))
                .map((ac, time) -> new Operation(ac.getOperationId(), ot, time, List.of(ac), user))
                .toList();
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(stainOps);
        Map<Integer, List<StainType>> opStains = Map.of(
                1, Arrays.asList(stainTypes),
                2, List.of(stainTypes[0])
        );
        when(mockStainTypeRepo.loadOperationStainTypes(any())).thenReturn(opStains);
        Action[] cytActions = {
                new Action(31, 3, slots[0], slots[0], null, null),
                new Action(41, 4, slots[1], slots[1], null, null),
        };
        List<CytData> data = Arrays.stream(cytActions).map(a -> new CytData(a, null)).toList();
        dataCompiler.loadStains(data, sourceSlotIds);
        verify(mockOpTypeRepo).getByName(eqCi("stain"));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(ot, sourceSlotIds);
        verify(mockStainTypeRepo).loadOperationStainTypes(Set.of(1,2));

        assertEquals("st1, st2, st3", data.get(0).row.getStainType());
        assertEquals("st1", data.get(1).row.getStainType());
        assertEquals(stainTimes[0], data.get(0).row.getStainPerformed());
        assertEquals(stainTimes[1], data.get(1).row.getStainPerformed());
        for (CytData d : data) {
            assertThat(d.users).containsExactly(user);
        }
    }

    @Test
    void testLoadImages() {
        OperationType opType = EntityFactory.makeOperationType("Image", null);
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        List<Slot> slots = lw.getSlots();
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        List<Operation> ops = List.of(makeOp(1, user, List.of()), makeOp(2, user, List.of()));
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);
        Map<Integer, Operation> latestOps = Map.of(
                slots.get(0).getId(), ops.get(0),
                slots.get(1).getId(), ops.get(1)
        );
        doReturn(latestOps).when(dataCompiler).latestOps(any());
        List<Action> cytActions = IntStream.range(0,2).mapToObj(i ->
                new Action(10*i+11, i+1, slots.get(i), slots.get(i), null, null)
        ).toList();
        List<CytData> data = cytActions.stream().map(a -> new CytData(a, null)).toList();
        Set<Integer> sourceSlotIds = slots.stream().map(Slot::getId).collect(toSet());
        dataCompiler.loadImages(data, sourceSlotIds);
        verify(mockOpTypeRepo).getByName(eqCi("image"));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, sourceSlotIds);
        for (int i = 0; i < 2; ++i) {
            assertEquals(ops.get(i).getPerformed(), data.get(i).row.getImagePerformed());
            assertThat(data.get(i).users).containsExactly(user);
        }
    }

    @Test
    void testLoadProbes() {
        OperationType opType = EntityFactory.makeOperationType("Probe hybridisation Cytassist", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        LabwareType lt = EntityFactory.makeLabwareType(1,1);
        User user = EntityFactory.getUser();
        Labware[] lws = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).toArray(Labware[]::new);
        List<Slot> slots = Arrays.stream(lws).map(Labware::getFirstSlot).toList();
        Set<Integer> sourceSlotIds = slots.stream().map(Slot::getId).collect(toSet());
        List<Action> cytActions = Zip.enumerate(slots.stream())
                .map((i, slot) -> new Action(10+i, 1, slot, slot, null, null))
                .toList();
        List<Action> probeActions = Zip.enumerate(slots.stream())
                .map((i,slot) -> new Action(20+i, i+1, slot, slot, null, null))
                .toList();
        List<Operation> probeOps = Zip.enumerate(probeActions.stream())
                .map((i, a) -> new Operation(i+1, opType, LocalDateTime.of(2025,8,i+1,12,0), List.of(a), user))
                .toList();
        List<Integer> probeOpIds = probeOps.stream().map(Operation::getId).toList();
        List<LabwareProbe> probes = List.of(
                new LabwareProbe(1, new ProbePanel(1, ProbePanel.ProbeType.cytassist, "p1"), probeOps.get(0).getId(), lws[0].getId(), "lot1", 100),
                new LabwareProbe(2, new ProbePanel(2, ProbePanel.ProbeType.cytassist, "p2"), probeOps.get(0).getId(), lws[0].getId(), "lot2", 200),
                new LabwareProbe(3, new ProbePanel(3, ProbePanel.ProbeType.cytassist, "p3"), probeOps.get(1).getId(), lws[1].getId(), "lot3", 300)
        );
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(probeOps);
        when(mockLwProbeRepo.findAllByOperationIdIn(any())).thenReturn(probes);
        List<CytData> data = cytActions.stream().map(a -> new CytData(a, null)).toList();
        dataCompiler.loadProbes(data, sourceSlotIds);
        verify(mockOpTypeRepo).getByName(eqCi(opType.getName()));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, sourceSlotIds);
        verify(mockLwProbeRepo).findAllByOperationIdIn(probeOpIds);
        for (int i = 0; i < 2; ++i) {
            CytassistOverview row = data.get(i).row;
            assertEquals(probeOps.get(i).getPerformed(), row.getProbeHybStart());
            assertEquals(i==0 ? "p1, p2" : "p3", row.getProbePanels());
            assertThat(data.get(i).users).containsExactly(user);
        }
    }

    @Test
    void testLoadProbeQC() {
        OperationType opType = EntityFactory.makeOperationType("Probe hybridisation QC", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        User user = EntityFactory.getUser();
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        List<Slot> slots = lw.getSlots();
        Set<Integer> sourceSlotIds = slots.stream().map(Slot::getId).collect(toSet());
        List<Operation> ops = List.of(
                makeOp(1, user, null),
                makeOp(2, user, null)
        );
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);
        Map<Integer, Operation> latestOps = Map.of(slots.get(0).getId(), ops.get(0), slots.get(1).getId(), ops.get(1));
        doReturn(latestOps).when(dataCompiler).latestOps(any());
        List<Action> cytActions = Zip.enumerate(slots.stream())
                .map((i, slot) -> new Action(10+i+1, i+1, slot, slot, null, null))
                .toList();
        List<CytData> data = cytActions.stream().map(a -> new CytData(a, null)).toList();
        dataCompiler.loadProbeQC(data, sourceSlotIds);
        verify(mockOpTypeRepo).getByName(eqCi(opType.getName()));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, sourceSlotIds);
        for (int i = 0; i < 2; ++i) {
            assertEquals(ops.get(i).getPerformed(), data.get(i).row.getProbeHybEnd());
            assertThat(data.get(i).users).containsExactly(user);
        }
    }

    @Test
    void testLoadTissueCoverage() {
        OperationType opType = EntityFactory.makeOperationType("Tissue coverage", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        Sample[] samples = EntityFactory.makeSamples(2);
        Labware lw = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,2), samples);
        List<Slot> slots = lw.getSlots();
        Set<Integer> sourceSlotIds = slots.stream().map(Slot::getId).collect(toSet());
        User user = EntityFactory.getUser();
        List<Operation> ops = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> makeOp(i, user, null))
                .toList();
        Set<Integer> opIds = ops.stream().map(Operation::getId).collect(toSet());
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);
        List<Action> cytActions = Zip.enumerate(slots.stream())
                .map((i, slot) -> new Action(10*i+11, i+1, slot, slot, samples[i], samples[i]))
                .toList();
        List<CytData> data = cytActions.stream().map(a -> new CytData(a, null)).toList();
        List<Measurement> measurements = Zip.enumerate(cytActions.stream())
                .map((i,a) -> new Measurement(i, "Tissue coverage", "value"+i, samples[i].getId(), ops.get(i).getId(), slots.get(i).getId()))
                .toList();
        when(mockMeasurementRepo.findAllByOperationIdIn(any())).thenReturn(measurements);

        dataCompiler.loadTissueCoverage(data, sourceSlotIds);
        verify(mockOpTypeRepo).getByName(eqCi(opType.getName()));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, sourceSlotIds);
        verify(mockMeasurementRepo).findAllByOperationIdIn(opIds);

        for (int i = 0; i < 2; ++i) {
            assertEquals("value"+i, data.get(i).row.getTissueCoverage());
            assertThat(data.get(i).users).containsExactly(user);
        }
    }

    @Test
    void testLoadQPCR() {
        Posterity posterity = new Posterity();
        List<CytData> data = List.of(new CytData(null, null));
        Set<Integer> allDestSlotIds = Set.of(1,2);
        doAnswer(invocation -> {
            List<CytData> argData = invocation.getArgument(0);
            BiConsumer<CytassistOverview, String> consumer = invocation.getArgument(5);
            consumer.accept(argData.getFirst().row, "20");
            return null;
        }).when(dataCompiler).loadMeasurement(any(), any(), any(), any(), any(), any());
        dataCompiler.loadQPCR(data, posterity, allDestSlotIds);
        verify(dataCompiler).loadMeasurement(same(data), same(posterity), same(allDestSlotIds),
                eqCi("qPCR"), eqCi("Cq value"), any());
        assertEquals("20", data.getFirst().row.getQpcrResult());
    }

    @Test
    void testLoadAmpCq() {
        Posterity posterity = new Posterity();
        List<CytData> data = List.of(new CytData(null, null));
        Set<Integer> allDestSlotIds = Set.of(1,2);
        doAnswer(invocation -> {
            List<CytData> argData = invocation.getArgument(0);
            BiConsumer<CytassistOverview, String> consumer = invocation.getArgument(5);
            consumer.accept(argData.getFirst().row, "30");
            return null;
        }).when(dataCompiler).loadMeasurement(any(), any(), any(), any(), any(), any());
        dataCompiler.loadAmpCq(data, posterity, allDestSlotIds);
        verify(dataCompiler).loadMeasurement(same(data), same(posterity), same(allDestSlotIds),
                eqCi("Amplification"), eqCi("Cq value"), any());
        assertEquals("30", data.getFirst().row.getAmplificationCq());
    }

    @Test
    void testLoadDualIndex() {
        OperationType opType = EntityFactory.makeOperationType("Dual index plate", null);
        when(mockOpTypeRepo.getByName(eqCi(opType.getName()))).thenReturn(opType);
        Posterity posterity = mock(Posterity.class);
        Set<Integer> allDestSlotIds = Set.of(1,2);
        User user = EntityFactory.getUser();
        Sample[] samples = EntityFactory.makeSamples(2);
        final LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware lw = EntityFactory.makeLabware(lt, samples);
        List<Slot> slots = lw.getSlots();
        Labware lw2 = EntityFactory.makeLabware(lt, samples);
        List<Slot> futureSlots = lw2.getSlots();
        List<Operation> diOps = IntStream.rangeClosed(1,2).mapToObj(i ->
            new Operation(i, opType, LocalDateTime.of(2025,8,i,12,0), List.of(), user)
        ).toList();
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(diOps);
        List<ReagentPlate> rps = IntStream.rangeClosed(1,2)
                .mapToObj(i -> EntityFactory.makeReagentPlate("RP"+i))
                .toList();
        rps.get(1).setPlateType("other plate type");
        List<ReagentAction> ras = IntStream.range(0, rps.size())
                .mapToObj(i -> new ReagentAction(100+i, diOps.get(i).getId(), rps.get(i).getSlot(new Address(1, 1+i)), futureSlots.get(i)))
                .toList();
        when(mockReagentActionRepo.findAllByOperationIdIn(any())).thenReturn(ras);
        when(mockReagentPlateRepo.findAllById(any())).thenReturn(rps);
        List<Action> cytActions = Zip.enumerate(slots.stream())
                .map((i,a) -> new Action(i, i, slots.get(i), slots.get(i), samples[i], samples[i]))
                .toList();
        List<CytData> data = cytActions.stream().map(a -> new CytData(a, null)).toList();
        for (int i = 0; i < 2; ++i) {
            when(posterity.descendents(new SlotSample(slots.get(i), samples[i]))).thenReturn(
                    new LinkedHashSet<>(List.of(new SlotSample(slots.get(i), samples[i]), new SlotSample(futureSlots.get(i), samples[i])))
            );
        }
        dataCompiler.loadDualIndex(data, posterity, allDestSlotIds);
        verify(mockOpTypeRepo).getByName(eqCi(opType.getName()));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, allDestSlotIds);
        verify(mockReagentActionRepo).findAllByOperationIdIn(diOps.stream().map(Operation::getId).collect(toSet()));

        for (int i = 0; i < 2; ++i) {
            CytData d = data.get(i);
            assertThat(d.users).containsExactly(user);
            assertEquals("A"+(i+1), d.row.getDualIndexPlateWell());
            assertEquals(rps.get(i).getPlateType(), d.row.getDualIndexPlateType());
        }
    }

    @Test
    void testLoadVisiumConcentration() {
        OperationType opType = EntityFactory.makeOperationType("Visium concentration", null);
        when(mockOpTypeRepo.getByName(eqCi(opType.getName()))).thenReturn(opType);
        Posterity mockPosterity = mock(Posterity.class);
        Set<Integer> allDestSlotIds = Set.of(1,2);
        User user = EntityFactory.getUser();
        Sample[] samples = EntityFactory.makeSamples(2);
        Sample[] futureSamples = EntityFactory.makeSamples(2);
        List<Sample> futureSampleList = Arrays.asList(futureSamples);
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware lw = EntityFactory.makeLabware(lt, samples);
        Labware lw2 = EntityFactory.makeLabware(lt, samples);
        List<Slot> slots = lw.getSlots();
        List<Slot> futureSlots = lw2.getSlots();
        List<Operation> ops = IntStream.range(0,2).mapToObj(i -> makeOp(i+1, user, futureSlots.subList(i,i+1), futureSlots.subList(i,i+1), futureSampleList.subList(i,i+1))).toList();
        Set<Integer> opIds = ops.stream().map(Operation::getId).collect(toSet());
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);
        List<Measurement> measurements = List.of(
                new Measurement(101, "cDNA concentration", "50", futureSamples[0].getId(), 1, futureSlots.get(0).getId()),
                new Measurement(102, "Library concentration", "60", futureSamples[1].getId(), 2, futureSlots.get(1).getId()),
                new Measurement(103, "Average size", "15", futureSamples[0].getId(), 1, futureSlots.get(0).getId())
        );
        Comment com = new Comment(1, "10-20", "size range");
        OperationComment opcom = new OperationComment(21, com, ops.getFirst().getId(), futureSamples[0].getId(), futureSlots.getFirst().getId(), com.getId());
        when(mockMeasurementRepo.findAllByOperationIdIn(any())).thenReturn(measurements);
        when(mockOpComRepo.findAllByOperationIdIn(any())).thenReturn(List.of(opcom));
        for (int i = 0; i < 2; ++i) {
            SlotSample ss = new SlotSample(slots.get(i), samples[i]);
            SlotSample futureSs = new SlotSample(futureSlots.get(i), futureSamples[i]);
            when(mockPosterity.descendents(ss)).thenReturn(new LinkedHashSet<>(List.of(ss, futureSs)));
        }
        List<Action> cytActions = IntStream.range(0,2)
                .mapToObj(i -> new Action(300+i, ops.get(i).getId(), slots.get(i), slots.get(i), samples[i], samples[i]))
                .toList();
        List<CytData> data = cytActions.stream().map(a -> new CytData(a, null)).toList();
        dataCompiler.loadVisiumConcentration(data, mockPosterity, allDestSlotIds);
        verify(mockOpTypeRepo).getByName(eqCi(opType.getName()));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, allDestSlotIds);
        verify(mockMeasurementRepo).findAllByOperationIdIn(opIds);
        verify(mockOpComRepo).findAllByOperationIdIn(opIds);
        CytData d = data.get(0);
        assertEquals("cDNA concentration", d.row.getVisiumConcentrationType());
        assertEquals("50", d.row.getVisiumConcentrationValue());
        assertEquals("15", d.row.getVisiumConcentrationAverageSize());
        assertEquals("10-20", d.row.getVisiumConcentrationRange());
        assertThat(d.users).containsExactly(user);
        d = data.get(1);
        assertEquals("Library concentration", d.row.getVisiumConcentrationType());
        assertEquals("60", d.row.getVisiumConcentrationValue());
        assertNull(d.row.getVisiumConcentrationAverageSize());
        assertNull(d.row.getVisiumConcentrationRange());
        assertThat(d.users).containsExactly(user);
    }

    static LocalDateTime time(int n) {
        return LocalDateTime.of(2025, 8, n+1, 12, 0);
    }

    @Test
    void testLoadLatestLabware() {
        User user = EntityFactory.getUser();
        Posterity mockPosterity = mock(Posterity.class);
        LabwareType lt = EntityFactory.makeLabwareType(1,1);
        Sample sample = EntityFactory.getSample();
        Labware[] startLw = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeLabware(lt, sample)).toArray(Labware[]::new);
        Slot[] startSlots = Arrays.stream(startLw).map(Labware::getFirstSlot).toArray(Slot[]::new);
        Labware[] leafLw = IntStream.range(0,4).mapToObj(i -> EntityFactory.makeLabware(lt, sample)).toArray(Labware[]::new);
        Slot[] leafSlots = Arrays.stream(leafLw).map(Labware::getFirstSlot).toArray(Slot[]::new);
        leafLw[1].setReleased(true);
        leafLw[2].setReleased(true);
        leafLw[3].setDiscarded(true);
        Set<Integer> leafLwIds = Arrays.stream(leafLw).map(Labware::getId).collect(toSet());
        when(mockPosterity.getLeafs()).thenReturn(Arrays.stream(leafSlots)
                .map(slot -> new SlotSample(slot, sample))
                .collect(toSet()));
        List<Release> releases = List.of(
                new Release(0, leafLw[0], user, null, null, null, time(7)),
                new Release(1, leafLw[1], user, null, null, null, time(8)),
                new Release(2, leafLw[2], user, null, null, null, time(9))
        );
        when(mockReleaseRepo.findAllByLabwareIdIn(any())).thenReturn(releases);
        when(mockLwRepo.findAllByIdIn(leafLwIds)).thenReturn(Arrays.asList(leafLw));
        List<Action> cytActions = List.of(
                new Action(11, 1, startSlots[0], startSlots[0], sample, sample),
                new Action(21, 2, startSlots[1], startSlots[1], sample, sample)
        );
        List<CytData> data = cytActions.stream().map(a -> new CytData(a, null)).toList();
        when(mockPosterity.descendents(new SlotSample(startSlots[0], sample))).thenReturn(new LinkedHashSet<>(List.of(
                new SlotSample(startSlots[0], sample), new SlotSample(leafSlots[0], sample), new SlotSample(leafSlots[1], sample)
        )));
        when(mockPosterity.descendents(new SlotSample(startSlots[1], sample))).thenReturn(new LinkedHashSet<>(List.of(
                new SlotSample(startSlots[1], sample), new SlotSample(leafSlots[2], sample), new SlotSample(leafSlots[3], sample)
        )));
        when(mockOpRepo.findEarliestPerformedIntoLabware(leafLwIds)).thenReturn(Map.of(
                leafLw[0].getId(), time(1),
                leafLw[1].getId(), time(2),
                leafLw[2].getId(), time(3),
                leafLw[3].getId(), time(4)
        ));
        dataCompiler.loadLatestLabware(data, mockPosterity);
        verify(mockLwRepo).findAllByIdIn(leafLwIds);
        Set<Integer> releasedLeafLwIds = Arrays.stream(leafLw).filter(Labware::isReleased).map(Labware::getId).collect(toSet());
        verify(mockReleaseRepo).findAllByLabwareIdIn(releasedLeafLwIds);
        verify(mockOpRepo).findEarliestPerformedIntoLabware(leafLwIds);
        CytData d = data.get(0);
        assertEquals(leafLw[1].getBarcode(), d.row.getLatestBarcode());
        assertEquals("released", d.row.getLatestState());
        assertEquals(time(8), d.row.getLatestBarcodeReleased());
        d = data.get(1);
        assertEquals(leafLw[3].getBarcode(), d.row.getLatestBarcode());
        assertEquals("discarded", d.row.getLatestState());
        assertNull(d.row.getLatestBarcodeReleased());
    }

    @Test
    void testLoadFlags() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sam = EntityFactory.getSample();
        Labware[] lws = IntStream.range(0, 7).mapToObj(i -> EntityFactory.makeLabware(lt, sam)).toArray(Labware[]::new);
        Slot[] slots = Arrays.stream(lws).map(Labware::getFirstSlot).toArray(Slot[]::new);
        Action[] cytActions = {
                new Action(11,1, slots[0], slots[1], sam, sam),
                new Action(21,2, slots[2], slots[3], sam, sam),
        };
        List<CytData> data = Arrays.stream(cytActions).map(a -> new CytData(a, null)).toList();
        data.get(0).sourceLabware = lws[0];
        data.get(0).destLabware = lws[1];
        data.get(1).sourceLabware = lws[2];
        data.get(1).destLabware = lws[3];
        Posterity mockPosterity = mock(Posterity.class);
        when(mockPosterity.descendents(new SlotSample(slots[0], sam))).thenReturn(new LinkedHashSet<>(List.of(
                new SlotSample(slots[0], sam),
                new SlotSample(slots[1], sam), new SlotSample(slots[4], sam), new SlotSample(slots[5], sam)
        )));
        when(mockPosterity.descendents(new SlotSample(slots[2], sam))).thenReturn(new LinkedHashSet<>(List.of(
                new SlotSample(slots[2], sam),
                new SlotSample(slots[3], sam), new SlotSample(slots[5], sam), new SlotSample(slots[6], sam)
        )));
        when(mockPosterity.keySet()).thenReturn(Arrays.stream(slots).map(slot -> new SlotSample(slot, sam)).collect(toSet()));
        Object[] flagData = {
                0, "Alpha", 0, "Beta", 3, "Gamma", 5, "Delta", 5, "Epsilon", 6, "Epsilon", 6, "Zeta"
        };
        List<LabwareFlag> lwFlags = new ArrayList<>(flagData.length/2);
        for (int i = 0; i < flagData.length; i += 2) {
            lwFlags.add(new LabwareFlag(i+1, lws[(int) flagData[i]], (String) flagData[i+1], null, null, null));
        }
        when(mockLwFlagRepo.findAllByLabwareIdIn(any())).thenReturn(lwFlags);
        dataCompiler.loadFlags(data, mockPosterity);
        verify(mockLwFlagRepo).findAllByLabwareIdIn(Arrays.stream(lws).map(Labware::getId).collect(toSet()));
        assertThat(data.get(0).row.getFlags().split("; ")).containsExactlyInAnyOrder("Alpha", "Beta", "Delta", "Epsilon");
        assertThat(data.get(1).row.getFlags().split("; ")).containsExactlyInAnyOrder("Gamma", "Delta", "Epsilon", "Zeta");
    }

    @Test
    void testSetUsers() {
        List<CytData> data = IntStream.range(0,3).mapToObj(i -> new CytData(null, null)).toList();
        User[] users = IntStream.rangeClosed(1,3).mapToObj(i -> new User(i, "user"+i, User.Role.normal)).toArray(User[]::new);
        data.get(0).users.add(users[0]);
        data.get(0).users.add(users[1]);
        data.get(1).users.add(users[2]);
        dataCompiler.setUsers(data);
        assertThat(data.get(0).row.getUsers().split(", ")).containsExactlyInAnyOrder("user1", "user2");
        assertEquals("user3", data.get(1).row.getUsers());
        assertThat(data.get(2).row.getUsers()).isNullOrEmpty();
    }

    @Test
    void testMeasurementApplies() {
        Sample[] samples = EntityFactory.makeSamples(2);
        Labware lw = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,2), samples);
        List<Slot> slots = lw.getSlots();
        Measurement[] measurements = IntStream.range(0,3).mapToObj(i ->
            new Measurement(i+1, "mname", "mvalue", i==0 ? null : samples[0].getId(), null, i==1 ? null : slots.getFirst().getId())
        ).toArray(Measurement[]::new);
        SlotIdSampleId[] ss = {new SlotIdSampleId(slots.get(1), samples[1]),
                new SlotIdSampleId(slots.get(0), samples[1]),
                new SlotIdSampleId(slots.get(1), samples[0]),
                new SlotIdSampleId(slots.get(0), samples[0])};
        boolean[][] expected = {{false,true,false,true}, {false,false,true,true}, {false,false,false,true}};
        for (int i = 0; i < measurements.length; i++) {
            boolean[] exp = expected[i];
            for (int j = 0; j < ss.length; j++) {
                assertEquals(exp[j], CytassistOverviewDataCompilerImp.measurementApplies(measurements[i], ss[j]));
            }
        }
    }

    @Test
    void testMeasurementApplies_mult() {
        Sample[] samples = EntityFactory.makeSamples(2);
        Labware lw = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,2), samples);
        List<Slot> slots = lw.getSlots();
        Measurement measurement = new Measurement(1, "mname", "mvalue", samples[0].getId(), null, slots.getFirst().getId());
        assertFalse(CytassistOverviewDataCompilerImp.measurementApplies(measurement, List.of(new SlotIdSampleId(slots.get(1), samples[0]), new SlotIdSampleId(slots.get(0), samples[1]))));
        assertTrue(CytassistOverviewDataCompilerImp.measurementApplies(measurement, List.of(new SlotIdSampleId(slots.get(1), samples[0]), new SlotIdSampleId(slots.get(0), samples[0]))));
    }

    @Test
    void testCommentApplies() {
        Sample[] samples = EntityFactory.makeSamples(2);
        Labware lw = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,2), samples);
        List<Slot> slots = lw.getSlots();
        OperationComment[] opcoms = IntStream.range(0,3).mapToObj(i ->
                new OperationComment(i+1, null, null, i==0 ? null : samples[0].getId(), i==1 ? null : slots.getFirst().getId(), null)
        ).toArray(OperationComment[]::new);
        SlotIdSampleId[] ss = {new SlotIdSampleId(slots.get(1), samples[1]),
                new SlotIdSampleId(slots.get(0), samples[1]),
                new SlotIdSampleId(slots.get(1), samples[0]),
                new SlotIdSampleId(slots.get(0), samples[0])};
        boolean[][] expected = {{false,true,false,true}, {false,false,true,true}, {false,false,false,true}};
        for (int i = 0; i < opcoms.length; i++) {
            boolean[] exp = expected[i];
            for (int j = 0; j < ss.length; j++) {
                assertEquals(exp[j], CytassistOverviewDataCompilerImp.commentApplies(opcoms[i], ss[j]));
            }
        }
    }

    @Test
    void testCommentApplies_mult() {
        Sample[] samples = EntityFactory.makeSamples(2);
        Labware lw = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,2), samples);
        List<Slot> slots = lw.getSlots();
        OperationComment opcom = new OperationComment(1, null, null, samples[0].getId(), slots.getFirst().getId(), null);
        assertFalse(CytassistOverviewDataCompilerImp.commentApplies(opcom, List.of(new SlotIdSampleId(slots.get(1), samples[0]), new SlotIdSampleId(slots.get(0), samples[1]))));
        assertTrue(CytassistOverviewDataCompilerImp.commentApplies(opcom, List.of(new SlotIdSampleId(slots.get(1), samples[0]), new SlotIdSampleId(slots.get(0), samples[0]))));
    }

    @Test
    void testRaSupersedes() {
        Map<Integer, Operation> opMap = IntStream.rangeClosed(1,2).mapToObj(i -> makeOp(i, null, null))
                .collect(inMap(Operation::getId));
        ReagentAction[] ras = IntStream.range(0,3).mapToObj(i ->
                i==2 ? null : new ReagentAction(10+i, i+1, null, null)
        ).toArray(ReagentAction[]::new);
        int[][] tests = {
                {0,1, 0},
                {1,0, 1},
                {0,2, 1},
                {2,0, 0},
        };
        for (int[] test : tests) {
            assertEquals(test[2]!=0, CytassistOverviewDataCompilerImp.raSupersedes(ras[test[0]], ras[test[1]], opMap));
        }
    }

    @Test
    void testSourceSlotIds() {
        LabwareType lt =  EntityFactory.makeLabwareType(1,6);
        List<Slot> slots = EntityFactory.makeEmptyLabware(lt).getSlots();
        Action[] actions = IntStream.range(0, 3).mapToObj(i ->
                new Action(10*i+11, i+1, slots.get(i), slots.get(i+3), null, null)
        ).toArray(Action[]::new);
        Set<Integer> sourceSlotIds = slots.subList(0,3).stream().map(Slot::getId).collect(toSet());
        List<CytData> data = Arrays.stream(actions).map(a -> new CytData(a, null)).toList();
        assertEquals(sourceSlotIds, dataCompiler.sourceSlotIds(data));
    }

    @Test
    void testDestSlotIds() {
        LabwareType lt =  EntityFactory.makeLabwareType(1,3);
        Sample sam = EntityFactory.getSample();
        List<Slot> slots = EntityFactory.makeEmptyLabware(lt).getSlots();
        Set<SlotSample> keyset = slots.stream().map(slot -> new SlotSample(slot, sam)).collect(toSet());
        Posterity mockPosterity = mock(Posterity.class);
        when(mockPosterity.keySet()).thenReturn(keyset);
        Set<Integer> destSlotIds = slots.stream().map(Slot::getId).collect(toSet());
        assertEquals(destSlotIds, dataCompiler.destSlotIds(mockPosterity));
    }

    @Test
    void testLatestOps() {
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        List<Slot> slots = lw.getSlots();
        Operation op1 = makeOp(1, null, slots);
        Operation op2 = makeOp(2, null, slots.subList(1,2));
        Map<Integer, Operation> latestOps = dataCompiler.latestOps(List.of(op1, op2));
        assertEquals(op1, latestOps.get(slots.get(0).getId()));
        assertEquals(op2, latestOps.get(slots.get(1).getId()));
    }


    @Test
    void testCompileMapToSet() {
        Function<String, Integer> fn = String::length;
        List<String> strings = List.of("Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta");
        Map<Integer, Set<String>> expected = Map.of(
                5, Set.of("Alpha", "Gamma", "Delta"),
                4, Set.of("Beta", "Zeta"),
                7, Set.of("Epsilon")
        );
        assertEquals(expected, CytassistOverviewDataCompilerImp.compileMapToSet(fn, strings));
    }

    @Test
    void testLoadMeasurement() {
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        User user = EntityFactory.getUser();
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        Set<Integer> destSlotIds = Set.of(10,11,12);
        Sample[] samples = EntityFactory.makeSamples(8);
        Labware lw = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,8), samples);
        List<Slot> slots = lw.getSlots();
        SlotSample[] sss = Zip.of(slots.stream(), Arrays.stream(samples)).map(SlotSample::new).toArray(SlotSample[]::new);
        Posterity mockPosterity = mock(Posterity.class);
        List<Operation> ops = IntStream.rangeClosed(1,4).mapToObj(i -> makeOp(i, user, null)).toList();
        List<Measurement> measurements = List.of(
                new Measurement(51, "mn", "v1", samples[4].getId(), 1, slots.get(4).getId()),
                new Measurement(52, "mn", "v2", samples[5].getId(), 2, slots.get(5).getId()),
                new Measurement(53, "mn", "v3", samples[6].getId(), 3, slots.get(6).getId()),
                new Measurement(54, "banana", "v4", samples[7].getId(), 4, slots.get(7).getId()),
                new Measurement(55, "banana", "v5", samples[7].getId(), 4, slots.get(7).getId())
        );
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);
        Set<Integer> opIds = ops.stream().map(Operation::getId).collect(toSet());
        when(mockMeasurementRepo.findAllByOperationIdIn(any())).thenReturn(measurements);
        when(mockPosterity.descendents(sss[0])).thenReturn(new LinkedHashSet<>(
                List.of(sss[0], sss[2], sss[3], sss[4])
        ));
        when(mockPosterity.descendents(sss[1])).thenReturn(new LinkedHashSet<>(
                List.of(sss[1], sss[4], sss[5], sss[6], sss[7])
        ));
        Action[] cytActions = {
                new Action(11, 1, slots.get(0), slots.get(0), samples[0], samples[0]),
                new Action(21, 2, slots.get(1), slots.get(1), samples[1], samples[1]),
        };
        List<CytData> data = Arrays.stream(cytActions).map(a -> new CytData(a, null)).toList();

        dataCompiler.loadMeasurement(data, mockPosterity, destSlotIds, "opname", "mn",
                CytassistOverview::setTissueCoverage);
        verify(mockOpTypeRepo).getByName(eqCi(opType.getName()));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, destSlotIds);
        verify(mockMeasurementRepo).findAllByOperationIdIn(opIds);
        verify(mockPosterity).descendents(sss[0]);
        verify(mockPosterity).descendents(sss[1]);
        assertEquals("v1", data.get(0).row.getTissueCoverage());
        assertEquals("v3", data.get(1).row.getTissueCoverage());
    }

    private static Operation makeOp(int id, User user, List<Slot> sources) {
        return makeOp(id, user, sources, sources, null);
    }

    private static Operation makeOp(int id, User user, List<Slot> sources, List<Slot> dests, List<Sample> samples) {
        List<Action> actions;
        if (sources==null) {
            actions =  List.of();
        } else {
            actions = IntStream.range(0, sources.size()).mapToObj(i -> {
                        Sample sam = (samples==null) ? null : samples.get(i);
                        return new Action(10 * id + i, id, sources.get(i), dests.get(i), sam, sam);
                    })
                    .toList();
        }
        return new Operation(id, null, LocalDateTime.of(2025,8, id, 12,0), actions, user);
    }
}