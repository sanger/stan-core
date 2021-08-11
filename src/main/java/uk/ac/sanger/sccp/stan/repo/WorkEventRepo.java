package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.WorkEvent;

/**
 * Repo for {@link WorkEvent work events}
 * @author dr6
 */
public interface WorkEventRepo extends CrudRepository<WorkEvent, Integer> {
}
