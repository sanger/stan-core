package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.PlanAction;

import java.util.List;
import java.util.OptionalInt;

public interface PlanActionRepo extends CrudRepository<PlanAction, Integer> {
    @Query(value = "SELECT MAX(CAST(a.new_section AS UNSIGNED)) FROM plan_action a WHERE a.source_slot_id=?1", nativeQuery = true)
    Integer _findMaxPlannedSectionFromSlotId(int sourceSlotId);

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
