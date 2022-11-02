package uk.ac.sanger.sccp.stan.service.operation;

import uk.ac.sanger.sccp.stan.model.*;

import java.util.Collection;
import java.util.Map;

public interface OpSearcher {
    /**
     * Finds the latest generational op of the given type for each given labware.
     * @param opType the type of operation to find
     * @param labware the labware to find operations for
     * @return a map of labware id to operation
     */
    Map<Integer, Operation> findLabwareOps(OperationType opType, Collection<Labware> labware);
}
