package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.SamplePositionRepo;
import uk.ac.sanger.sccp.stan.repo.SlotRegionRepo;
import uk.ac.sanger.sccp.stan.request.SamplePositionResult;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class SlotRegionServiceImp implements SlotRegionService {
    private final LabwareRepo lwRepo;
    private final SlotRegionRepo slotRegionRepo;
    private final SamplePositionRepo samplePositionRepo;

    @Autowired
    public SlotRegionServiceImp(LabwareRepo lwRepo, SlotRegionRepo slotRegionRepo, SamplePositionRepo samplePositionRepo) {
        this.lwRepo = lwRepo;
        this.slotRegionRepo = slotRegionRepo;
        this.samplePositionRepo = samplePositionRepo;
    }

    @Override
    public List<SamplePositionResult> loadSamplePositionResultsForLabware(String barcode) {
        Labware lw = lwRepo.getByBarcode(barcode);
        Map<Integer, Slot> slotIdMap = lw.getSlots().stream().collect(BasicUtils.inMap(Slot::getId));
        List<SamplePosition> sps = samplePositionRepo.findAllBySlotIdIn(slotIdMap.keySet());
        if (sps.isEmpty()) {
            return List.of();
        }
        return sps.stream()
                .map(sp -> toSamplePositionResult(sp, slotIdMap))
                .collect(toList());
    }

    /**
     * Converts a SamplePosition (entity) to a SamplePositionResult (GraphQL return type).
     * @param sp a sample position
     * @param slotIdMap a map to look up slots by their id
     * @return a SamplePositionResult for the given SamplePosition
     */
    public SamplePositionResult toSamplePositionResult(SamplePosition sp, Map<Integer, Slot> slotIdMap) {
        return new SamplePositionResult(slotIdMap.get(sp.getSlotId()), sp.getSampleId(), sp.getSlotRegion().getName());
    }

    @Override
    public Iterable<SlotRegion> loadSlotRegions(boolean includeDisabled) {
        return (includeDisabled ? slotRegionRepo.findAll() : slotRegionRepo.findAllByEnabled(true));
    }

    @Override
    public boolean anyMissingRegions(Stream<Map.Entry<Address, String>> addressRegionStream) {
        final Map<Address, Integer> addressRegionCount = new HashMap<>();
        final Set<Address> addressesWithNoRegion = new HashSet<>();
        Iterable<Map.Entry<Address, String>> addressRegions = addressRegionStream::iterator;
        for (var e : addressRegions) {
            Address address = e.getKey();
            if (address != null) {
                addressRegionCount.merge(address, 1, Integer::sum);
                if (nullOrEmpty(e.getValue())) {
                    addressesWithNoRegion.add(address);
                }
            }
        }
        return (addressesWithNoRegion.stream().anyMatch(ad -> addressRegionCount.get(ad) > 1));
    }

    @Override
    public Set<String> validateSlotRegions(UCMap<SlotRegion> slotRegionMap,
                                           Stream<Map.Entry<Address, String>> addressRegionStream) {
        final LinkedHashSet<String> problems = new LinkedHashSet<>();
        final Map<Address, Set<SlotRegion>> addressRegions = new HashMap<>();
        addressRegionStream.forEach(e -> {
            Address address = e.getKey();
            String string = e.getValue();
            SlotRegion region = slotRegionMap.get(string);
            if (region==null) {
                problems.add("Unknown region: "+repr(string));
            } else {
                var regions = addressRegions.get(address);
                if (regions==null) {
                    regions = new HashSet<>();
                    regions.add(region);
                    addressRegions.put(address, regions);
                } else if (!regions.add(region)) {
                    problems.add(String.format("Region %s repeated in slot address %s.", region.getName(), address));
                }
            }
        });
        return problems;
    }
}
