package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.Roi;
import uk.ac.sanger.sccp.stan.request.LabwareRoi;

import java.util.Collection;
import java.util.List;

/**
 * Service for helping with {@link Roi Rois}
 */
public interface RoiService {
    /**
     * Loads the rois in the indicated labware
     * @param barcodes barcodes of labware. Barcodes that do not match any labware are ignored.
     * @return details of the rois found in labware with the given barcodes
     */
    List<LabwareRoi> labwareRois(Collection<String> barcodes);
}
