package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.Slot;

import java.util.Collection;
import java.util.Set;

/**
 * Service to check for cleaned out slots
 */
public interface CleanedOutSlotService {
    /**
     * Finds any slots in the given labware that have been cleaned out
     * @param labware the labware to find cleaned out slots in
     * @return the slots from the given labware that have been cleaned out
     */
    Set<Slot> findCleanedOutSlots(Collection<Labware> labware);
}
