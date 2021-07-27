package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.OperationComment;

import java.util.Collection;
import java.util.List;

public interface OperationCommentRepo extends CrudRepository<OperationComment, Integer> {
    List<OperationComment> findAllByOperationIdIn(Collection<Integer> operationIds);
}
