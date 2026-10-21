package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AddressString;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.utils.Zip;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;
import static uk.ac.sanger.sccp.utils.BasicUtils.toLinkedHashSet;

/**
 * Tests {@link MeasurementServiceImp}
 */
class TestMeasurementService {
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private ActionRepo mockActionRepo;
    @Mock
    private MeasurementRepo mockMeasurementRepo;
    @Mock
    private Ancestoriser mockAncestoriser;

    @InjectMocks
    private MeasurementServiceImp service;

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

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testGetMeasurementsFromLabwareOrParent(boolean any) {
        final String name = "x";
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(lt, sample, sample);
        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        Map<SlotIdSampleId, Set<Slot>> slotIdMap = Map.of(
                new SlotIdSampleId(100,200), Set.of(lw.getSlot(A1)),
                new SlotIdSampleId(101,201), Set.of(lw.getSlot(A2))
        );
        doReturn(slotIdMap).when(service).getParentSlotIdMap(any());
        List<Measurement> measurements;
        if (any) {
            measurements = List.of(
                    new Measurement(1, name, "1", sample.getId(), 1, lw.getSlot(A1).getId()),
                    new Measurement(2, name, "2", 200, 2, 100),
                    new Measurement(3, name, "3", 201, 3, 101)
            );
        } else {
            measurements = List.of();
        }
        when(mockMeasurementRepo.findAllBySlotIdInAndName(any(), any())).thenReturn(measurements);

        Map<Address, List<Measurement>> map = service.getMeasurementsFromLabwareOrParent(lw.getBarcode(), name);
        Set<Integer> allSlotIds = Stream.concat(lw.getSlots().stream().map(Slot::getId), Stream.of(100,101))
                .collect(toSet());
        verify(service).getParentSlotIdMap(lw.getSlots());
        verify(mockMeasurementRepo).findAllBySlotIdInAndName(sameElements(allSlotIds, false), eq(name));
        if (any) {
            assertThat(map).hasSize(2);
            assertThat(map.get(A1)).containsExactlyInAnyOrderElementsOf(measurements.subList(0, 2));
            assertThat(map.get(A2)).containsExactly(measurements.get(2));
        } else {
            assertThat(map).isEmpty();
        }
    }

    @Test
    void testMakeSsMeasurementMap() {
        final int[] idCounter = {10};
        BiFunction<Integer, Integer, Measurement> newMeas = (slotId, sampleId) -> new Measurement(++idCounter[0], "", "", sampleId, 2000, slotId);
        List<Measurement> measurements = List.of(newMeas.apply(10, 20), newMeas.apply(10, 21), newMeas.apply(11, 21), newMeas.apply(11, 21));
        Map<SlotIdSampleId, Measurement> expected = Map.of(
                new SlotIdSampleId(10, 20), measurements.get(0),
                new SlotIdSampleId(10, 21), measurements.get(1),
                new SlotIdSampleId(11, 21), measurements.get(3)
        );
        assertThat(service.makeSsMeasurementMap(measurements)).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void testFindMeasurement() {
        Address A1 = new Address(1, 1);
        IntFunction<Slot> newSlot = slotId -> new Slot(slotId, 1, A1, null);
        Slot[] slots = IntStream.range(10, 13).mapToObj(newSlot).toArray(Slot[]::new);
        Sample[] samples = EntityFactory.makeSamples(3);
        Ancestoriser.SlotSample[] slotSamples = Zip.of(Arrays.stream(slots), Arrays.stream(samples)).map(Ancestoriser.SlotSample::new)
                .toArray(Ancestoriser.SlotSample[]::new);
        Ancestoriser.Ancestry ancestry = mock(Ancestoriser.Ancestry.class);
        Set<Ancestoriser.SlotSample> slotSampleSet = Arrays.stream(slotSamples).collect(toLinkedHashSet());
        when(ancestry.ancestors(slotSamples[0])).thenReturn(slotSampleSet);

        Measurement m1 = new Measurement(1, "", "", samples[1].getId(), 2000, slots[1].getId());
        Measurement m2 = new Measurement(2, "", "", samples[2].getId(), 2000, slots[2].getId());
        Map<SlotIdSampleId, Measurement> map = Stream.of(m1, m2).collect(inMap(m -> new SlotIdSampleId(m.getSlotId(), m.getSampleId())));

        assertSame(m1, service.findMeasurement(ancestry, map, slotSamples[0]));
    }

    @Test
    void testGetMeasurementsFromLabwareOrAncestor() {
        String measName = "count";
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        Sample[] samples = EntityFactory.makeSamples(2);
        Slot[] slots = lw.getSlots().toArray(new Slot[0]);
        Ancestoriser.SlotSample[] slotSamples = new Ancestoriser.SlotSample[slots.length];
        for (int i = 0; i < slotSamples.length; ++i) {
            slots[i].addSample(samples[i]);
            slotSamples[i] = new Ancestoriser.SlotSample(slots[i], samples[i]);
        }
        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);

        Ancestoriser.Ancestry ancestry = mock(Ancestoriser.Ancestry.class);
        when(mockAncestoriser.findAncestry(any())).thenReturn(ancestry);
        when(ancestry.keySet()).thenReturn(Set.of(slotSamples));
        Set<Integer> slotIds = Set.of(slots[0].getId(), slots[1].getId());

        List<Measurement> measurements = List.of(new Measurement(1, measName, "A", samples[0].getId(), 1000, slots[0].getId()),
                new Measurement(2, measName, "B", samples[1].getId(), 1000, slots[1].getId()));
        when(mockMeasurementRepo.findAllBySlotIdInAndName(any(), any())).thenReturn(measurements);

        Map<SlotIdSampleId, Measurement> measurementMap = measurements.stream().collect(inMap(m -> new SlotIdSampleId(m.getSlotId(), m.getSampleId())));
        doReturn(measurementMap).when(service).makeSsMeasurementMap(any());

        doReturn(measurements.get(0)).when(service).findMeasurement(ancestry, measurementMap, slotSamples[0]);
        doReturn(measurements.get(1)).when(service).findMeasurement(ancestry, measurementMap, slotSamples[1]);

        Map<Address, List<Measurement>> result = service.getMeasurementsFromLabwareOrAncestor(lw.getBarcode(), measName);

        assertThat(result).hasSize(2);
        assertThat(result.get(A1)).containsExactly(measurements.get(0));
        assertThat(result.get(A2)).containsExactly(measurements.get(1));

        verify(mockLwRepo).getByBarcode(lw.getBarcode());
        verify(mockAncestoriser).findAncestry(Arrays.asList(slotSamples));
        verify(ancestry).keySet();
        verify(mockMeasurementRepo).findAllBySlotIdInAndName(slotIds, measName);
        verify(service).makeSsMeasurementMap(measurements);
        verify(service).findMeasurement(ancestry, measurementMap, slotSamples[0]);
        verify(service).findMeasurement(ancestry, measurementMap, slotSamples[1]);
    }

    @Test
    public void testGetParentSlotIdMap() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(lt, sample, sample);
        Labware parent = EntityFactory.makeLabware(lt, sample, sample);
        List<Action> actions = List.of(
                new Action(1, 1, parent.getSlot(A1), lw.getSlot(A2), sample, sample),
                new Action(2, 2, parent.getSlot(A2), lw.getSlot(A1), sample, sample),
                new Action(3, 3, parent.getSlot(A2), lw.getSlot(A2), sample, sample)
        );
        when(mockActionRepo.findAllByDestinationIn(any())).thenReturn(actions);

        List<Slot> slots = lw.getSlots();
        Map<SlotIdSampleId, Set<Slot>> map = service.getParentSlotIdMap(slots);

        verify(mockActionRepo).findAllByDestinationIn(slots);

        assertThat(map).hasSize(2);
        assertThat(map.get(new SlotIdSampleId(parent.getSlot(A1).getId(), sample.getId())))
                .containsExactly(lw.getSlot(A2));
        assertThat(map.get(new SlotIdSampleId(parent.getSlot(A2).getId(), sample.getId())))
                .containsExactlyInAnyOrder(lw.getSlot(A1), lw.getSlot(A2));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testToAddressStrings(boolean any) {
        final String name = "alpha";
        Map<Address, List<Measurement>> map;
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        if (any) {
            map = Map.of(A1, List.of(new Measurement(1, name, "1", 1, 2, 3)),
                    A2, List.of(new Measurement(2, name, "2", 1, 2, 3),
                            new Measurement(3, name, "3", 1, 2, 3)));
        } else {
            map = Map.of();
        }
        List<AddressString> results = service.toAddressStrings(map);
        if (!any) {
            assertThat(results).isEmpty();
            return;
        }
        assertThat(results).containsExactlyInAnyOrder(
                new AddressString(A1, "1"), new AddressString(A2, "3")
        );
    }
}