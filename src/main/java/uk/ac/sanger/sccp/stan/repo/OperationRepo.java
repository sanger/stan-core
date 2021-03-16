package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Operation;
import uk.ac.sanger.sccp.stan.model.OperationType;

import java.util.Collection;
import java.util.List;

public interface OperationRepo extends CrudRepository<Operation, Integer> {
    @Query("select distinct op from Operation op join Action a on (a.operationId=op.id) " +
            "where op.operationType=?1 and a.sample.id in (?2)")
    List<Operation> findAllByOperationTypeAndSampleIdIn(OperationType opType, Collection<Integer> sampleIds);
}
