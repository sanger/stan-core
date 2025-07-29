package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.OperationComment;
import uk.ac.sanger.sccp.stan.model.OperationType;

import java.util.Collection;
import java.util.List;

public interface OperationCommentRepo extends CrudRepository<OperationComment, Integer> {
    List<OperationComment> findAllByOperationIdIn(Collection<Integer> operationIds);

    @Query("select oc from OperationComment oc join Operation op on (oc.operationId=op.id) " +
            "where oc.slotId in (?1) and op.operationType=?2")
    List<OperationComment> findAllBySlotAndOpType(Collection<Integer> slotIds, OperationType opType);

    @Query("select oc from OperationComment oc join Comment com on (oc.comment=com) " +
            "where oc.slotId in (?1) and com.category=?2")
    List<OperationComment> findAllBySlotIdInAndCommentCategory(Collection<Integer> slotIds, String category);
}
