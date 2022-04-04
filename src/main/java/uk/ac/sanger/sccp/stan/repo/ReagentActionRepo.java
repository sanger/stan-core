package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentAction;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;

import java.util.Collection;
import java.util.List;

public interface ReagentActionRepo extends CrudRepository<ReagentAction, Integer> {
    List<ReagentAction> findAllByOperationIdIn(Collection<Integer> opIds);

    List<ReagentAction> findAllByDestinationIdIn(Collection<Integer> slotIds);
}
