package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.PlanAction;

public interface PlanActionRepo extends CrudRepository<PlanAction, Integer> {
}
