package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.RoiRepo;
import uk.ac.sanger.sccp.stan.request.LabwareRoi;
import uk.ac.sanger.sccp.stan.request.LabwareRoi.RoiResult;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/**
 * @author dr6
 */
@Service
public class RoiServiceImp implements RoiService {
    private final LabwareRepo lwRepo;
    private final RoiRepo roiRepo;

    @Autowired
    public RoiServiceImp(LabwareRepo lwRepo, RoiRepo roiRepo) {
        this.lwRepo = lwRepo;
        this.roiRepo = roiRepo;
    }

    @Override
    public List<LabwareRoi> labwareRois(Collection<String> barcodes) {
        requireNonNull(barcodes, "Barcodes is null");
        List<Labware> labware = lwRepo.findByBarcodeIn(barcodes);
        if (labware.isEmpty()) {
            return List.of();
        }
        Map<Integer, Slot> slotIdMap = labware.stream()
                .flatMap(lw -> lw.getSlots().stream())
                .collect(inMap(Slot::getId));
        List<Roi> rois = roiRepo.findAllBySlotIdIn(slotIdMap.keySet());
        Map<Integer, List<RoiResult>> lwIdRoiResults = rois.stream()
                .map(roi -> toRoiResult(roi, slotIdMap))
                .collect(groupingBy(rr -> slotIdMap.get(rr.getSlotId()).getLabwareId()));
        return labware.stream()
                .map(lw -> new LabwareRoi(lw.getBarcode(), lwIdRoiResults.get(lw.getId())))
                .toList();
    }

    /**
     * Converts a roi to a roiresult (for passing back through graphql)
     * @param roi the roi to convert
     * @param slotIdMap map to look up slot by id
     * @return a roiresult object suitable for passing via graphql
     */
    RoiResult toRoiResult(Roi roi, Map<Integer, Slot> slotIdMap) {
        Slot slot = slotIdMap.get(roi.getSlotId());
        return new RoiResult(roi.getSlotId(), slot.getAddress(), roi.getSampleId(), roi.getOperationId(), roi.getRoi());
    }
}
