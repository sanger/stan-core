package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.PlanOperation;

public interface PlanOperationRepo extends CrudRepository<PlanOperation, Integer> {
}
