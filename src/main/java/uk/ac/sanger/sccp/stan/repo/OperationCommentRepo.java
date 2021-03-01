package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.OperationComment;

public interface OperationCommentRepo extends CrudRepository<OperationComment, Integer> {
}
