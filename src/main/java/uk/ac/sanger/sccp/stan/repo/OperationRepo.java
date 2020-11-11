package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Operation;

public interface OperationRepo extends CrudRepository<Operation, Integer> {
}
