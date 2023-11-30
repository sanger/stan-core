package uk.ac.sanger.sccp.stan.service.flag;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareFlagRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.service.flag.FlagLookupServiceImp.OpIdLwId;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/** Tests {@link FlagLookupServiceImp} */
class TestFlagLookupService {
    @Mock
    private Ancestoriser mockAncestoriser;
    @Mock
    private LabwareFlagRepo mockFlagRepo;
    @Mock
    private OperationRepo mockOpRepo;

    @InjectMocks
    private FlagLookupServiceImp service;

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

    static List<Labware> getLabware(int number) {
        if (number < 1) {
            return List.of();
        }
        Labware lw1 = EntityFactory.getTube();
        Sample sample = lw1.getFirstSlot().getSamples().get(0);
        LabwareType lt = lw1.getLabwareType();
        return Stream.concat(Stream.of(lw1), IntStream.range(1, number).mapToObj(i -> EntityFactory.makeLabware(lt, sample)))
                .collect(toList());

    }

    @Test
    void testLookUp() {
        Ancestry mockAncestry = mock(Ancestry.class);
        doReturn(mockAncestry).when(service).loadAncestry(any());
        Labware lw1 = EntityFactory.getTube();
        Sample sam = lw1.getFirstSlot().getSamples().get(0);
        Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), sam);
        Labware lw3 = EntityFactory.makeLabware(lw1.getLabwareType(), sam);
        List<Labware> labware = List.of(lw1, lw2);
        Set<SlotSample> slotSamples = Stream.of(lw1, lw2, lw3)
                .map(Labware::getFirstSlot)
                .map(slot -> new SlotSample(slot, sam))
                .collect(toSet());
        when(mockAncestry.keySet()).thenReturn(slotSamples);
        LabwareFlag flag = new LabwareFlag(100, lw3, "flag alpha", null, 200);
        Map<SlotSample, List<LabwareFlag>> directFlags = Map.of(new SlotSample(lw3.getFirstSlot(), sam), List.of(flag));
        doReturn(directFlags).when(service).loadDirectFlags(any());

        doReturn(List.of(flag)).when(service).flagsForLabware(mockAncestry, lw1, directFlags);
        doReturn(List.of()).when(service).flagsForLabware(mockAncestry, lw2, directFlags);

        UCMap<List<LabwareFlag>> result = service.lookUp(labware);
        assertEquals(List.of(flag), result.get(lw1.getBarcode()));
        assertThat(result.get(lw2.getBarcode())).isNullOrEmpty();

        verify(service).loadAncestry(labware);
        verify(service).loadDirectFlags(slotSamples);
        verify(service, times(labware.size())).flagsForLabware(any(), any(), any());
    }

    @Test
    void testLookUp_noLabware() {
        assertThat(service.lookUp(List.of())).isEmpty();
        verify(service, never()).loadAncestry(any());
    }

    @Test
    void testLookUp_noFlags() {
        Ancestry mockAncestry = mock(Ancestry.class);
        doReturn(mockAncestry).when(service).loadAncestry(any());
        Labware lw1 = EntityFactory.getTube();
        Slot slot = lw1.getFirstSlot();
        Set<SlotSample> slotSamples = Set.of(new SlotSample(slot, slot.getSamples().get(0)));
        when(mockAncestry.keySet()).thenReturn(slotSamples);
        doReturn(Map.of()).when(service).loadDirectFlags(any());

        final List<Labware> labware = List.of(lw1);
        assertThat(service.lookUp(labware)).isEmpty();
        verify(service).loadAncestry(labware);
        verify(service).loadDirectFlags(slotSamples);
        verify(service, never()).flagsForLabware(any(), any(), any());
    }

    @Test
    void testLoadAncestry() {
        List<Labware> labware = getLabware(2);
        List<SlotSample> expectedSlotSamples = labware.stream()
                .flatMap(lw -> lw.getSlots().stream())
                .flatMap(slot -> slot.getSamples().stream().map(sam -> new SlotSample(slot, sam)))
                .collect(toList());
        Ancestry ancestry = mock(Ancestry.class);
        when(mockAncestoriser.findAncestry(any())).thenReturn(ancestry);
        assertSame(ancestry, service.loadAncestry(labware));
        verify(mockAncestoriser).findAncestry(expectedSlotSamples);
    }

    @Test
    void testLoadDirectFlags() {
        List<Labware> labware = getLabware(2);
        List<Integer> lwIds = labware.stream().map(Labware::getId).collect(toList());
        Set<SlotSample> slotSamples = labware.stream().flatMap(SlotSample::stream).collect(toSet());
        List<LabwareFlag> flags = List.of(new LabwareFlag(100, labware.get(0), "flag0", null, 200),
                new LabwareFlag(101, labware.get(0), "flag1", null, 201),
                new LabwareFlag(102, labware.get(1), "flag2", null, 202));
        when(mockFlagRepo.findAllByLabwareIdIn(any())).thenReturn(flags);
        Map<SlotSample, List<LabwareFlag>> ssFlagMap = Map.of(slotSamples.iterator().next(), flags);
        doReturn(ssFlagMap).when(service).makeSsFlagMap(any(), any());

        assertSame(ssFlagMap, service.loadDirectFlags(slotSamples));

        verify(mockFlagRepo).findAllByLabwareIdIn(new HashSet<>(lwIds));
        verify(service).makeSsFlagMap(Set.of(200,201,202),
                Map.of(new OpIdLwId(200, lwIds.get(0)), flags.subList(0,1),
                        new OpIdLwId(201, lwIds.get(0)), flags.subList(1,2),
                        new OpIdLwId(202, lwIds.get(1)), flags.subList(2,3)));
    }

    @Test
    void testLoadDirectFlags_noFlags() {
        Labware lw = EntityFactory.getTube();
        Slot slot = lw.getFirstSlot();
        Set<SlotSample> slotSamples = Set.of(new SlotSample(slot, slot.getSamples().get(0)));
        when(mockFlagRepo.findAllByLabwareIdIn(any())).thenReturn(List.of());
        assertThat(service.loadDirectFlags(slotSamples)).isEmpty();
        verify(mockFlagRepo).findAllByLabwareIdIn(Set.of(lw.getId()));
    }

    @Test
    void testMakeSsFlagMap() {
        List<Labware> labware = getLabware(2);
        List<SlotSample> slotSamples = labware.stream()
                .map(Labware::getFirstSlot)
                .map(slot -> new SlotSample(slot, slot.getSamples().get(0)))
                .collect(toList());
        List<Operation> ops = labware.stream()
                .map(List::of)
                .map(lwList -> EntityFactory.makeOpForLabware(null, lwList, lwList))
                .collect(toList());
        when(mockOpRepo.findAllById(any())).thenReturn(ops);
        List<LabwareFlag> flags = IntStream.range(0, labware.size())
                .mapToObj(i -> new LabwareFlag(100+i, labware.get(i), "flag"+i, null, ops.get(i).getId()))
                .collect(toList());
        Map<OpIdLwId, List<LabwareFlag>> opIdLwIdMap = flags.stream()
                .collect(toMap(flag -> new OpIdLwId(flag.getOperationId(), flag.getLabware().getId()), List::of));
        Set<Integer> opIds = ops.stream().map(Operation::getId).collect(toSet());

        Map<SlotSample, List<LabwareFlag>> expected = IntStream.range(0, labware.size())
                .boxed()
                .collect(toMap(slotSamples::get, i -> flags.subList(i, i+1)));

        assertEquals(expected, service.makeSsFlagMap(opIds, opIdLwIdMap));

        verify(mockOpRepo).findAllById(opIds);
    }

    @Test
    void testFlagsForLabware() {
        Ancestry ancestry = mock(Ancestry.class);
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, null, sam1.getTissue(), sam1.getBioState());
        Labware lw = EntityFactory.makeLabware(lt, sam1, sam2);
        List<SlotSample> lwSs = SlotSample.stream(lw).collect(toList());
        Labware other = EntityFactory.makeLabware(lt, sam1, sam2);
        SlotSample ss1 = new SlotSample(other.getFirstSlot(), sam1);
        SlotSample ss2 = new SlotSample(other.getSlots().get(1), sam2);
        SlotSample ss3 = new SlotSample(other.getFirstSlot(), sam2);
        List<LabwareFlag> flags = IntStream.range(0,4)
                .mapToObj(i -> new LabwareFlag(10+i, null, null, null, null))
                .collect(toList());
        Map<SlotSample, List<LabwareFlag>> ssFlags = Map.of(
                ss1, List.of(flags.get(0), flags.get(1)),
                ss2, List.of(flags.get(2)),
                ss3, List.of(flags.get(3))
        );

        when(ancestry.ancestors(lwSs.get(0))).thenReturn(Set.of(lwSs.get(0), ss1));
        when(ancestry.ancestors(lwSs.get(1))).thenReturn(Set.of(lwSs.get(1), ss2));

        assertThat(service.flagsForLabware(ancestry, lw, ssFlags)).containsExactlyInAnyOrderElementsOf(flags.subList(0, 3));
    }
}