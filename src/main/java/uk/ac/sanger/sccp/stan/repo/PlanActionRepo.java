package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.PlanAction;

import java.util.List;
import java.util.OptionalInt;

public interface PlanActionRepo extends CrudRepository<PlanAction, Integer> {
    @Query("SELECT MAX(a.newSection) FROM PlanAction a WHERE a.sample.tissue.id=?1")
    Integer _findMaxPlannedSectionForTissueId(int tissueId);

    @Query("SELECT MAX(a.newSection) FROM PlanAction a WHERE a.source.id=?1")
    Integer _findMaxPlannedSectionFromSlotId(int sourceSlotId);

    /**
     * Finds the maximum section for planned actions for particular tissue
     * @param tissueId the id of tissue
     * @return the highest section number of any planned action on a sample with that tissue id
     */
    default OptionalInt findMaxPlannedSectionForTissueId(int tissueId) {
        Integer maxInteger = _findMaxPlannedSectionForTissueId(tissueId);
        return (maxInteger==null ? OptionalInt.empty() : OptionalInt.of(maxInteger));
    }

    /**
     * Finds the maximum section for planned actions from a particular slot
     * @param sourceSlotId the id of the source slot
     * @return the highest section number of any planned action on a sample from that slot
     */
    default OptionalInt findMaxPlannedSectionFromSlotId(int sourceSlotId) {
        Integer n = _findMaxPlannedSectionFromSlotId(sourceSlotId);
        return (n==null ? OptionalInt.empty() : OptionalInt.of(n));
    }

    List<PlanAction> findAllByDestinationLabwareId(int labwareId);
}
