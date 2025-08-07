package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabwareNote;
import uk.ac.sanger.sccp.stan.model.OperationType;

import java.util.Collection;
import java.util.List;

/**
 * Repo for saving and retrieving key/value string pairs
 * linked to a particular labware and operation.
 */
public interface LabwareNoteRepo extends CrudRepository<LabwareNote, Integer> {
    List<LabwareNote> findAllByOperationIdIn(Collection<Integer> opIds);
    List<LabwareNote> findAllByPlanIdIn(Collection<Integer> planIds);
    List<LabwareNote> findAllByLabwareIdInAndName(Collection<Integer> labwareIds, String name);
    boolean existsByNameAndValue(String name, String value);
    @Query("select ln from LabwareNote ln " +
            "join Operation op on (ln.operationId=op.id) " +
            "where ln.name=?1 and op.operationType=?3 " +
            "and ln.labwareId in (?2)")
    List<LabwareNote> findAllByNameAndLabwareIdInAndOperationType(String name, Collection<Integer> labwareIds, OperationType opType);
}
