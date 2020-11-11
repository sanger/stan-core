package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Action;

public interface ActionRepo extends CrudRepository<Action, Integer> {
}
