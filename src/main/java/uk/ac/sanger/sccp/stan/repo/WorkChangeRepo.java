package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.WorkChange;

public interface WorkChangeRepo extends CrudRepository<WorkChange, Integer> {
}
