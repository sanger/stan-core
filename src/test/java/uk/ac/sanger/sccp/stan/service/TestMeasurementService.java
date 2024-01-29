package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AddressString;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;

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