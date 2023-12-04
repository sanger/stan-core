package uk.ac.sanger.sccp.stan.service.flag;

import org.jetbrains.annotations.NotNull;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.LabwareFlag;
import uk.ac.sanger.sccp.stan.request.FlagDetail;
import uk.ac.sanger.sccp.stan.request.LabwareFlagged;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.Collection;
import java.util.List;

/**
 * Service for looking up {@link LabwareFlag flags} on labware
 */
public interface FlagLookupService {
    /**
     * Looks up flags on the given labware and its ancestors
     * @param labware the labware to look up flags on
     * @return a map from labware barcodes to the found flags
     */
    @NotNull
    UCMap<List<LabwareFlag>> lookUp(Collection<Labware> labware);

    /**
     * Converts a map of flags to details for serialisation
     * @param flagMap a map of labware barcode to applicable flags
     * @return a list of flag details
     */
    List<FlagDetail> toDetails(UCMap<List<LabwareFlag>> flagMap);

    /**
     * Looks up flags and converts them to details
     * @param labware the labware to get flag details for
     * @return the flag details applicable to the given labware
     */
    default List<FlagDetail> lookUpDetails(Collection<Labware> labware) {
        return toDetails(lookUp(labware));
    }

    /**
     * Checks if the labware is flagged and returns a {@code LabwareFlagged} object.
     * @param lw the labware to check
     * @return an object specifying a labware and whether or not there are any applicable flags
     */
    LabwareFlagged getLabwareFlagged(Labware lw);
}
