package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Action;
import uk.ac.sanger.sccp.stan.model.Slot;

import java.util.Collection;
import java.util.List;

public interface ActionRepo extends CrudRepository<Action, Integer> {
    List<Action> findAllByDestinationIn(Collection<Slot> destinations);
}
