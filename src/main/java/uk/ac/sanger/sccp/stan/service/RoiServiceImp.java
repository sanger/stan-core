package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.LabwareRoi;
import uk.ac.sanger.sccp.stan.request.LabwareRoi.RoiResult;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/**
 * @author dr6
 */
@Service
public class RoiServiceImp implements RoiService {
    private final LabwareRepo lwRepo;
    private final RoiRepo roiRepo;
    private final SampleRepo sampleRepo;

    @Autowired
    public RoiServiceImp(LabwareRepo lwRepo, RoiRepo roiRepo, SampleRepo sampleRepo) {
        this.lwRepo = lwRepo;
        this.roiRepo = roiRepo;
        this.sampleRepo = sampleRepo;
    }

    @Override
    public List<LabwareRoi> labwareRois(Collection<String> barcodes) {
        requireNonNull(barcodes, "Barcodes is null");
        List<Labware> labware = lwRepo.findByBarcodeIn(barcodes);
        if (labware.isEmpty()) {
            return List.of();
        }
        final Map<Integer, Slot> slotIdMap = labware.stream()
                .flatMap(lw -> lw.getSlots().stream())
                .collect(inMap(Slot::getId));
        final List<Roi> rois = roiRepo.findAllBySlotIdIn(slotIdMap.keySet());
        final Map<Integer, Sample> sampleIdMap = loadSamples(labware, rois);
        final Map<Integer, List<RoiResult>> lwIdRoiResults = rois.stream()
                .map(roi -> toRoiResult(roi, slotIdMap, sampleIdMap))
                .collect(groupingBy(rr -> slotIdMap.get(rr.getSlotId()).getLabwareId()));
        return labware.stream()
                .map(lw -> new LabwareRoi(lw.getBarcode(), lwIdRoiResults.get(lw.getId())))
                .toList();
    }

    /**
     * Gets a map of samples referenced in the rois.
     * All samples inside the given labware are added to the map,
     * and any others mentioned in the rois are loaded from the SampleRepo.
     * @param labware the labware referenced in the ROIs
     * @param rois the ROIs we are loading information about
     * @return a map of samples from their IDs
     */
    public Map<Integer, Sample> loadSamples(Collection<Labware> labware, Collection<Roi> rois) {
        final Map<Integer, Sample> sampleMap = new HashMap<>();
        labware.stream()
                .flatMap(lw -> lw.getSlots().stream().flatMap(slot -> slot.getSamples().stream()))
                .forEach(sample -> sampleMap.put(sample.getId(), sample));
        Set<Integer> missingSampleIds = rois.stream()
                .map(Roi::getSampleId)
                .filter(id -> sampleMap.get(id)==null)
                .collect(toSet());
        if (!missingSampleIds.isEmpty()) {
            for (Sample sample : sampleRepo.findAllByIdIn(missingSampleIds)) {
                sampleMap.put(sample.getId(), sample);
            }
        }
        return sampleMap;
    }

    /**
     * Converts a roi to a roiresult (for passing back through graphql)
     * @param roi the roi to convert
     * @param slotIdMap map to look up slot by id
     * @param sampleIdMap map to look up sample by id
     * @return a roiresult object suitable for passing via graphql
     */
    RoiResult toRoiResult(Roi roi, Map<Integer, Slot> slotIdMap, Map<Integer, Sample> sampleIdMap) {
        Slot slot = slotIdMap.get(roi.getSlotId());
        return new RoiResult(roi.getSlotId(), slot.getAddress(), sampleIdMap.get(roi.getSampleId()), roi.getOperationId(), roi.getRoi());
    }
}
