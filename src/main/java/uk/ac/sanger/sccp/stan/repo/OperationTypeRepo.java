package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.OperationType;

public interface OperationTypeRepo extends CrudRepository<OperationType, Integer> {
}
