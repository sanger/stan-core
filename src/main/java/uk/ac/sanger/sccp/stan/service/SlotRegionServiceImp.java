package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.SamplePositionResult;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

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
}
