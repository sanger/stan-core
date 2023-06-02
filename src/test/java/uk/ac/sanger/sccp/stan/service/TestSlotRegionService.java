package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.SamplePositionRepo;
import uk.ac.sanger.sccp.stan.repo.SlotRegionRepo;
import uk.ac.sanger.sccp.stan.request.SamplePositionResult;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Test {@link SlotRegionServiceImp} */
@ExtendWith(MockitoExtension.class)
public class TestSlotRegionService {
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private SlotRegionRepo mockSlotRegionRepo;
    @Mock
    private SamplePositionRepo mockSamplePositionRepo;

    @InjectMocks
    private SlotRegionServiceImp service;

    @Test
    void testLoadSamplePositionResultsForLabware() {
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, null, sam1.getTissue(), sam1.getBioState());
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        Slot slot1 = lw.getFirstSlot();
        slot1.setSamples(List.of(sam1, sam2));
        Slot slot2 = lw.getSlot(new Address(1,2));
        slot2.setSamples(List.of(sam1));
        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        SlotRegion top = new SlotRegion(1, "Top");
        SlotRegion bottom = new SlotRegion(2, "Bottom");
        final Integer opId = 50;
        List<SamplePosition> samplePositions = List.of(
                new SamplePosition(slot1.getId(), sam1.getId(), top, opId),
                new SamplePosition(slot1.getId(), sam2.getId(), bottom, opId)
        );
        when(mockSamplePositionRepo.findAllBySlotIdIn(any())).thenReturn(samplePositions);

        List<SamplePositionResult> results = service.loadSamplePositionResultsForLabware(lw.getBarcode());
        verify(mockSamplePositionRepo).findAllBySlotIdIn(lw.getSlots().stream().map(Slot::getId).collect(toSet()));
        assertThat(results).containsExactly(
                new SamplePositionResult(slot1, sam1.getId(), top.getName()),
                new SamplePositionResult(slot1, sam2.getId(), bottom.getName())
        );
    }

    @Test
    void testToSamplePositionResult() {
        Slot slot = EntityFactory.getTube().getFirstSlot();
        Integer sampleId = slot.getSamples().get(0).getId();
        Integer opId = 30;
        Map<Integer, Slot> slotMap = Map.of(slot.getId(), slot);
        SamplePosition samPos = new SamplePosition(slot.getId(), sampleId, new SlotRegion(1, "Top"), opId);
        assertEquals(new SamplePositionResult(slot.getId(), slot.getAddress(), sampleId, "Top"),
                service.toSamplePositionResult(samPos, slotMap));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLoadSlotRegions(boolean includeDisabled) {
        List<SlotRegion> regions = List.of(new SlotRegion(1, "Top"), new SlotRegion(2, "Bottom"));
        when(includeDisabled ? mockSlotRegionRepo.findAll() : mockSlotRegionRepo.findAllByEnabled(true))
                .thenReturn(regions);
        assertSame(regions, service.loadSlotRegions(includeDisabled));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLostSlotRegionMap(boolean includeDisabled) {
        List<SlotRegion> regions = List.of(new SlotRegion(1, "Top"), new SlotRegion(2, "Bottom"));
        when(includeDisabled ? mockSlotRegionRepo.findAll() : mockSlotRegionRepo.findAllByEnabled(true))
                .thenReturn(regions);
        UCMap<SlotRegion> map = service.loadSlotRegionMap(includeDisabled);
        assertThat(map).hasSize(2);
        assertSame(regions.get(0), map.get("top"));
        assertSame(regions.get(1), map.get("bottom"));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testAnyMissingRegions(boolean anyMissing) {
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        Stream<Map.Entry<Address, String>> stream = Stream.of(
                Map.entry(A1, ""),
                Map.entry(A2, "Alpha"),
                Map.entry(A2, anyMissing ? "" : "Beta")
        );
        assertEquals(anyMissing, service.anyMissingRegions(stream));
    }

    @ParameterizedTest
    @MethodSource("validateSlotRegionsArgs")
    void testValidateSlotRegions(UCMap<SlotRegion> regionMap, Collection<Map.Entry<Address, String>> entries,
                                 Collection<String> expectedProblems) {
        assertThat(service.validateSlotRegions(regionMap, entries.stream()))
                .containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateSlotRegionsArgs() {
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final UCMap<SlotRegion> regionMap = UCMap.from(SlotRegion::getName,
                new SlotRegion(1, "Top"), new SlotRegion(2, "Bottom"));
        return Arrays.stream(new Object[][] {
                {
                    List.of(Map.entry(A1, "Top"), Map.entry(A2, "Bottom")),
                        List.of(),
                },
                {
                    List.of(Map.entry(A1, "Top"), Map.entry(A2, "Bananas")),
                        List.of("Unknown region: \"Bananas\""),
                },
                {
                    List.of(Map.entry(A1, "top"), Map.entry(A1, "TOP")),
                        List.of("Region Top repeated in slot address A1."),
                },
                {
                    List.of(Map.entry(A1, "top"), Map.entry(A1, "top"), Map.entry(A2, "Bananas")),
                        List.of("Region Top repeated in slot address A1.", "Unknown region: \"Bananas\""),
                },
        }).map(arg -> Arguments.of(regionMap, arg[0], arg[1]));
    }
}