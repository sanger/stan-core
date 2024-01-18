package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.MeasurementRepo;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.VisiumPermData.AddressPermData;
import uk.ac.sanger.sccp.stan.service.flag.FlagLookupService;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.service.VisiumPermDataService.*;

/**
 * Tests {@link VisiumPermDataService}
 */
public class TestVisiumPermDataService {
    private LabwareRepo mockLwRepo;
    private MeasurementRepo mockMeasurementRepo;
    private Ancestoriser mockAncestoriser;
    private VisiumPermDataService service;
    private SlotRegionService mockSlotRegionService;
    private FlagLookupService mockFlagLookupService;

    @BeforeEach
    void setUp() {
        mockLwRepo = mock(LabwareRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);
        mockAncestoriser = mock(Ancestoriser.class);
        mockSlotRegionService = mock(SlotRegionService.class);
        mockFlagLookupService = mock(FlagLookupService.class);
        service = new VisiumPermDataService(mockLwRepo, mockMeasurementRepo, mockAncestoriser,
                mockSlotRegionService, mockFlagLookupService);
    }

    @Test
    public void testLoad() {
        LabwareType lt = EntityFactory.makeLabwareType(3,2);
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(lt, sample, sample, sample);
        LabwareFlagged lf = new LabwareFlagged(lw, false);
        when(mockFlagLookupService.getLabwareFlagged(lw)).thenReturn(lf);
        final Slot slot = lw.getFirstSlot();
        Ancestry ancestry = new Ancestry();
        when(mockAncestoriser.findAncestry(any())).thenReturn(ancestry);
        final SlotSample slotSample = new SlotSample(slot, sample);
        ancestry.put(slotSample, Set.of(slotSample));
        Integer sampleId = sample.getId();
        Integer opId = 100;
        final Address A1 = new Address(1,1);
        List<Measurement> measurements = List.of(
                new Measurement(100, PERM_TIME, "120", sampleId, opId, slot.getId())
        );
        SamplePositionResult samplePositionResult = new SamplePositionResult(slot, sampleId, "Top", opId);
        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        when(mockMeasurementRepo.findAllBySlotIdIn(any())).thenReturn(measurements);
        when(mockSlotRegionService.loadSamplePositionResultsForLabware(lw.getBarcode())).thenReturn(List.of(samplePositionResult));
        VisiumPermData pd = service.load(lw.getBarcode());
        assertSame(lf, pd.getLabware());
        assertThat(pd.getAddressPermData()).containsExactly(new AddressPermData(A1, 120));
        assertThat(pd.getSamplePositionResults()).containsExactly(samplePositionResult);
        verify(mockAncestoriser).findAncestry(SlotSample.stream(lw).collect(toList()));
        verify(mockFlagLookupService).getLabwareFlagged(lw);
    }

    @Test
    public void testMakeSlotSampleIdAddressMap() {
        Ancestry ancestry = new Ancestry();
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, null, sam1.getTissue(), sam1.getBioState());
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        Labware lw  = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,2), sam1, sam2);
        LabwareType lt = EntityFactory.getTubeType();
        Slot[] slots = IntStream.range(0, 3)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sam1).getFirstSlot())
                .toArray(Slot[]::new);
        ancestry.put(new SlotSample(lw.getSlot(A1), sam1), Set.of(new SlotSample(slots[0], sam1), new SlotSample(slots[1], sam1)));
        ancestry.put(new SlotSample(slots[0], sam1), Set.of(new SlotSample(slots[2], sam2)));
        ancestry.put(new SlotSample(lw.getSlot(A2), sam2), Set.of(new SlotSample(slots[0], sam2)));

        var result = service.makeSlotSampleIdAddressMap(lw, ancestry);
        Map<SlotIdSampleId, Set<Address>> expected = new HashMap<>(6);

        expected.put(new SlotIdSampleId(lw.getFirstSlot(), sam1), Set.of(A1));
        expected.put(new SlotIdSampleId(lw.getSlot(A2), sam2), Set.of(A2));
        expected.put(new SlotIdSampleId(slots[0], sam1), Set.of(A1));
        expected.put(new SlotIdSampleId(slots[1], sam1), Set.of(A1));
        expected.put(new SlotIdSampleId(slots[2], sam2), Set.of(A1));
        expected.put(new SlotIdSampleId(slots[0], sam2), Set.of(A2));
        assertThat(result).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    public void testCompilePermData() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2),
                A3 = new Address(1,3);
        Map<SlotIdSampleId, Set<Address>> ssAd = Map.of(
                new SlotIdSampleId(1, 11), Set.of(A1, A2),
                new SlotIdSampleId(2, 11), Set.of(A3),
                new SlotIdSampleId(2, 12), Set.of(A1)
        );

        List<Measurement> measurements = List.of(
                new Measurement(100, PERM_TIME, "120", 11, 80, 1),
                new Measurement(101, PERM_TIME, "121", 12, 81, 2),
                new Measurement(102, CONTROL, "positive", 11, 82, 2),
                new Measurement(103, SELECTED_TIME, "121", 12, 83, 2)
        );

        assertThat(service.compilePermData(measurements, ssAd)).containsExactlyInAnyOrder(
                new AddressPermData(A1, 120),
                new AddressPermData(A2, 120),
                new AddressPermData(A1, 121, null, true),
                new AddressPermData(A3, ControlType.positive)
        );
    }
}
