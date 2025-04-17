package uk.ac.sanger.sccp.stan.service.workchange;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OpWorkRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/** Test {@link WorkChangeServiceImp} */
class TestWorkChangeService {

    @Mock
    WorkChangeValidationService mockValidationService;
    @Mock
    WorkService mockWorkService;
    @Mock
    WorkRepo mockWorkRepo;
    @Mock
    OperationRepo mockOpRepo;
    @Mock
    ReleaseRepo mockReleaseRepo;

    @InjectMocks
    WorkChangeServiceImp service;

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

    private static Operation opWithId(int id) {
        Operation op = new Operation();
        op.setId(id);
        return op;
    }

    @Test
    void testPerform_invalid() {
        List<String> problems = List.of("Bad");
        doThrow(new ValidationException(problems)).when(mockValidationService).validate(any());
        OpWorkRequest request = new OpWorkRequest("SGP1", List.of(1));
        assertValidationException(() -> service.perform(request), problems);
        verify(service, never()).execute(any(), any());
    }

    @Test
    void testPerform_valid() {
        Work work = EntityFactory.makeWork("SGP1");
        List<Operation> ops = List.of(opWithId(1), opWithId(2));
        WorkChangeData data = new WorkChangeData(work, ops);
        when(mockValidationService.validate(any())).thenReturn(data);
        OpWorkRequest request = new OpWorkRequest("SGP1", List.of(1, 2));
        doReturn(ops).when(service).execute(any(), any());

        assertSame(ops, service.perform(request));
        verify(mockValidationService).validate(request);
        verify(service).execute(work, ops);
    }

    @Test
    void testExecute() {
        Work work = EntityFactory.makeWork("SGP1");
        List<Operation> ops = List.of(opWithId(1), opWithId(2));
        doNothing().when(service).clearOutPriorWorks(any());
        service.execute(work, ops);
        InOrder inOrder = inOrder(service, mockWorkService);
        inOrder.verify(service).clearOutPriorWorks(ops);
        inOrder.verify(mockWorkService).link(work, ops, true);
    }

    @Test
    void testLoadOpIdWorks() {
        Work[] works = IntStream.rangeClosed(1,3).mapToObj(i -> EntityFactory.makeWork("SGP"+i)).toArray(Work[]::new);
        List<Operation> ops = List.of(opWithId(1), opWithId(2));
        Map<Integer, Set<String>> opIdWorkNumbers = Map.of(
                1, Set.of("SGP1", "SGP2"),
                2, Set.of("SGP2", "SGP3")
        );
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, works);

        when(mockWorkRepo.findWorkNumbersForOpIds(any())).thenReturn(opIdWorkNumbers);
        when(mockWorkRepo.getMapByWorkNumberIn(any())).thenReturn(workMap);

        Map<Integer, Set<Work>> expected = Map.of(
                1, Set.of(works[0], works[1]),
                2, Set.of(works[1], works[2])
        );

        assertEquals(expected, service.loadOpIdWorks(ops));
        verify(mockWorkRepo).findWorkNumbersForOpIds(List.of(1,2));
        verify(mockWorkRepo).getMapByWorkNumberIn(Set.of("SGP1", "SGP2", "SGP3"));
    }

    @Test
    void testWorkExtantSlotSampleIds() {
        List<Work> works = IntStream.rangeClosed(1,4).mapToObj(i -> EntityFactory.makeWork("SGP"+i)).toList();
        works.get(0).setOperationIds(Set.of(1,2,4));
        works.get(1).setOperationIds(Set.of(2,3,4,5));
        works.get(2).setOperationIds(Set.of(3));
        works.get(2).setReleaseIds(Set.of(11,12));
        int[] workIds = works.stream().mapToInt(Work::getId).toArray();
        Map<Integer, Set<SlotIdSampleId>> opSsids = Map.of(
                1, Set.of(new SlotIdSampleId(20,30),
                        new SlotIdSampleId(21,31)),
                2, Set.of(new SlotIdSampleId(20,31),
                        new SlotIdSampleId(21,31)),
                3, Set.of(new SlotIdSampleId(22,32))
        );
        Map<Integer, Set<SlotIdSampleId>> releaseSsids = Map.of(
                11, Set.of(new SlotIdSampleId(20,30),
                        new SlotIdSampleId(20,33)),
                12, Set.of(new SlotIdSampleId(25,35))
        );
        when(mockOpRepo.findOpSlotSampleIds(any())).thenReturn(opSsids);
        when(mockReleaseRepo.findReleaseSlotSampleIds(any())).thenReturn(releaseSsids);

        Map<Integer, Set<SlotIdSampleId>> result = service.workExtantSlotSampleIds(works, Set.of(4,5,6));

        verify(mockOpRepo).findOpSlotSampleIds(Set.of(1,2,3));
        verify(mockReleaseRepo).findReleaseSlotSampleIds(Set.of(11,12));

        assertThat(result).containsKeys(workIds[0], workIds[1], workIds[2]);
        assertThat(result.get(workIds[0])).containsExactlyInAnyOrder(
                new SlotIdSampleId(20,30),
                new SlotIdSampleId(21,31),
                new SlotIdSampleId(20,31)
        );
        assertThat(result.get(workIds[1])).containsExactlyInAnyOrder(
                new SlotIdSampleId(20,31),
                new SlotIdSampleId(21,31),
                new SlotIdSampleId(22,32)
        );
        assertThat(result.get(workIds[2])).containsExactlyInAnyOrder(
                new SlotIdSampleId(22,32),
                new SlotIdSampleId(20,30),
                new SlotIdSampleId(20,33),
                new SlotIdSampleId(25,35)
        );
        assertThat(result.get(workIds[3])).isNullOrEmpty();
    }

    @Test
    void testGetOpSlotSampleIds() {
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        Sample[] sams = EntityFactory.makeSamples(3);
        Labware lw = EntityFactory.makeLabware(lt, sams);
        List<Slot> slots = lw.getSlots();
        List<Action> actions = List.of(
                new Action(1, 1, slots.get(0), slots.get(0), sams[0], sams[0]),
                new Action(2, 1, slots.get(0), slots.get(1), sams[1], sams[0]),
                new Action(3, 1, slots.get(2), slots.get(2), sams[2], sams[2]),
                new Action(4, 1, slots.get(2), slots.get(2), sams[1], sams[2])
        );
        Operation op = opWithId(1);
        op.setActions(actions);
        Set<SlotIdSampleId> result = service.getOpSlotSampleIds(op);
        assertThat(result).containsExactlyInAnyOrder(
                new SlotIdSampleId(slots.get(0), sams[0]),
                new SlotIdSampleId(slots.get(1), sams[1]),
                new SlotIdSampleId(slots.get(2), sams[2]),
                new SlotIdSampleId(slots.get(2), sams[1])
        );
    }

    @Test
    void testFindSampleSlotIdsToRemove() {
        Work[] works = IntStream.of(1,2).mapToObj(i -> EntityFactory.makeWork("SGP"+i)).toArray(Work[]::new);
        Map<Integer, Work> workIdMap = Arrays.stream(works).collect(inMap(Work::getId));
        List<Operation> ops = IntStream.of(1,2).mapToObj(TestWorkChangeService::opWithId).toList();
        Map<Integer, Set<Work>> opIdWorks = Map.of(1, Set.of(works[0]), 2, Set.of(works[1]));
        Map<Integer, Set<SlotIdSampleId>> extantWorkSsids = Map.of(
                works[0].getId(), Set.of(new SlotIdSampleId(10,20), new SlotIdSampleId(11,21)),
                works[1].getId(), Set.of(new SlotIdSampleId(12,22), new SlotIdSampleId(13,23))
        );
        doReturn(extantWorkSsids).when(service).workExtantSlotSampleIds(any(), any());
        Set<SlotIdSampleId> op1ssids = Set.of(new SlotIdSampleId(10,20), new SlotIdSampleId(13,23),
                new SlotIdSampleId(14,24), new SlotIdSampleId(15,25));
        Set<SlotIdSampleId> op2ssids = Set.of(new SlotIdSampleId(11,21), new SlotIdSampleId(12,22));
        doReturn(op1ssids).when(service).getOpSlotSampleIds(ops.get(0));
        doReturn(op2ssids).when(service).getOpSlotSampleIds(ops.get(1));

        Map<Integer, Set<Work.SampleSlotId>> toRemove = service.findSampleSlotIdsToRemove(ops, workIdMap, opIdWorks);
        verify(service).workExtantSlotSampleIds(workIdMap.values(), opIdWorks.keySet());

        assertThat(toRemove).containsKeys(works[0].getId(), works[1].getId());
        assertThat(toRemove.get(works[0].getId())).containsExactlyInAnyOrder(
                new Work.SampleSlotId(23,13),
                new Work.SampleSlotId(24,14),
                new Work.SampleSlotId(25,15)
        );
        assertThat(toRemove.get(works[1].getId())).containsExactly(new Work.SampleSlotId(21,11));
    }

    @Test
    void testClearOutPriorWorks() {
        List<Operation> ops = IntStream.of(1,2).mapToObj(TestWorkChangeService::opWithId).toList();
        Work[] works = IntStream.of(1,2).mapToObj(i -> EntityFactory.makeWork("SGP"+i)).toArray(Work[]::new);
        Map<Integer, Set<Work>> opIdWorks = Map.of(1, Set.of(works[0]), 2, Set.of(works[1]));
        Map<Integer, Work> workIdMap = Map.of(works[0].getId(), works[0], works[1].getId(), works[1]);
        Map<Integer, Set<Work.SampleSlotId>> toRemove = Map.of(
                works[0].getId(), Set.of(new Work.SampleSlotId(20,10), new Work.SampleSlotId(21,11)),
                works[1].getId(), Set.of(new Work.SampleSlotId(22,12), new Work.SampleSlotId(23,13))
        );

        works[0].setSampleSlotIds(new HashSet<>(Set.of(new Work.SampleSlotId(20,10), new Work.SampleSlotId(22,12))));
        works[1].setSampleSlotIds(new HashSet<>(Set.of(new Work.SampleSlotId(23,13), new Work.SampleSlotId(24,14))));

        works[0].setOperationIds(new HashSet<>(Set.of(1,2,3,4)));
        works[1].setOperationIds(new HashSet<>(Set.of(2,5)));

        doReturn(opIdWorks).when(service).loadOpIdWorks(ops);
        doReturn(toRemove).when(service).findSampleSlotIdsToRemove(ops, workIdMap, opIdWorks);

        service.clearOutPriorWorks(ops);
        verify(mockWorkRepo).saveAll(sameElements(workIdMap.values(), true));

        assertThat(works[0].getSampleSlotIds()).containsExactlyInAnyOrder(new Work.SampleSlotId(22,12));
        assertThat(works[1].getSampleSlotIds()).containsExactlyInAnyOrder(new Work.SampleSlotId(24,14));

        assertThat(works[0].getOperationIds()).containsExactlyInAnyOrder(3,4);
        assertThat(works[1].getOperationIds()).containsExactlyInAnyOrder(5);
    }
}