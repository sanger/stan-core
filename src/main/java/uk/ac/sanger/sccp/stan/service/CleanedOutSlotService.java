package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.*;

import java.util.*;

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

    /**
     * Finds addresses of cleaned out slots in labware with the given barcode.
     * Returns an empty list if there is no such labware.
     * @param barcode the barcode of the labware
     * @return the addresses of the cleaned out slots
     */
    List<Address> findCleanedOutAddresses(String barcode);
}
