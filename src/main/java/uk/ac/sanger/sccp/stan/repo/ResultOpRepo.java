package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.ResultOp;

import java.util.Collection;
import java.util.List;

public interface ResultOpRepo extends CrudRepository<ResultOp, Integer> {
    List<ResultOp> findAllByOperationIdIn(Collection<Integer> opIds);
}
