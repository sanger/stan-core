package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.SamplePosition;
import uk.ac.sanger.sccp.stan.model.SlotRegion;
import uk.ac.sanger.sccp.stan.request.SamplePositionResult;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static uk.ac.sanger.sccp.utils.BasicUtils.asList;

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

    /** Loads the results of {@link #loadSlotRegions} into a map from the regions' names */
    default UCMap<SlotRegion> loadSlotRegionMap(boolean includeDisabled) {
        return UCMap.from(asList(this.loadSlotRegions(includeDisabled)), SlotRegion::getName);
    }

    /** Looks for addresses with multiple entries which don't specify a region for each entry. */
    boolean anyMissingRegions(Stream<Map.Entry<Address, String>> addressRegionStream);

    /**
     * Checks for problems with the specified regions.
     * Regions include:<ul>
     *     <li>unknown region specified</li>
     *     <li>same region used for multiple samples in an address</li>
     * </ul>
     * @param slotRegionMap map of slot regions from names
     * @param addressRegions links between addresses and region names
     * @return any problems found
     */
    Set<String> validateSlotRegions(UCMap<SlotRegion> slotRegionMap,
                                    Stream<Map.Entry<Address, String>> addressRegions);
}
