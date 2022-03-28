package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Action;
import uk.ac.sanger.sccp.stan.model.Slot;

import java.util.Collection;
import java.util.List;

public interface ActionRepo extends CrudRepository<Action, Integer> {
    List<Action> findAllByDestinationIn(Collection<Slot> destinations);

    @Query("select distinct a.source.labwareId from Action a " +
            "where a.destination.labwareId IN (?1)")
    List<Integer> findSourceLabwareIdsForDestinationLabwareIds(Collection<Integer> destLabwareIds);
}
