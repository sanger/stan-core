package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.OperationType;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.model.slotcopyrecord.SlotCopyRecord;

import java.util.Optional;

public interface SlotCopyRecordRepo extends CrudRepository<SlotCopyRecord, Integer> {
    Optional<SlotCopyRecord> findByOperationTypeAndWorkAndLpNumber(OperationType opType, Work work, String lpNumber);
}
