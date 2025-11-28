package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.*;

import java.util.Collection;
import java.util.List;

/**
 * Service for creating and loading slot groups.
 */
public interface SlotGroupService {
    /**
     * Saves groups in the database.
     * @param lw the labware containing the grouped slots
     * @param planId the id of the plan creating the groups
     * @param groups the groups (collections of addresses)
     * @return the group records created in the database
     */
    List<SlotGroup> saveGroups(Labware lw, Integer planId, Collection<? extends Collection<Address>> groups);

    /**
     * Loads the groups (lists of addresses) for a specified plan.
     * @param planId the id of the plan
     * @return a list of groups (lists of slot addresses)
     */
    List<List<Address>> loadGroups(Integer planId);
}
