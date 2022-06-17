package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.OperationSolution;

import java.util.List;

public interface OperationSolutionRepo extends CrudRepository<OperationSolution, OperationSolution.OperationSolutionKey> {
    List<OperationSolution> findAllByOperationId(Integer operationId);
}
