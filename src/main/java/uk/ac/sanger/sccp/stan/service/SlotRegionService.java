package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.SamplePosition;
import uk.ac.sanger.sccp.stan.model.SlotRegion;
import uk.ac.sanger.sccp.stan.request.SamplePositionResult;

import java.util.List;

/**
 * Service dealing with {@link SlotRegion}s and {@link SamplePosition}s.
 */
public interface SlotRegionService {
    /**
     * Loads sample position results for the specified labware
     * @param barcode the barcode of the labware
     * @return the sample position results for that labware (if any)
     * @exception javax.persistence.EntityNotFoundException if no such labware exists
     */
    List<SamplePositionResult> loadSamplePositionResultsForLabware(String barcode);

    /**
     * Loads all slot regions, optionally including disabled ones
     * @param includeDisabled true to include disabled as well as enabled regions
     * @return the slot regions from the database
     */
    Iterable<SlotRegion> loadSlotRegions(boolean includeDisabled);
}
