package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import uk.ac.sanger.sccp.stan.model.PlanOperation;

import java.util.Collection;
import java.util.List;

public interface PlanOperationRepo extends CrudRepository<PlanOperation, Integer> {
    /**
     * Gets plans into the given destination labware
     * @param destinationIds the destination labware ids
     * @return any matching plans
     */
    @Query("select distinct po from PlanOperation po " +
            "inner join PlanAction pa on (pa.planOperationId=po.id) "+
            "where pa.destination.labwareId in (:destinationIds)")
    List<PlanOperation> findAllByDestinationIdIn(@Param("destinationIds") Collection<Integer> destinationIds);
}
