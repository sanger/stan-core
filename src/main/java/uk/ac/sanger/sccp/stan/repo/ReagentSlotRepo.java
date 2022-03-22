package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentSlot;

public interface ReagentSlotRepo extends CrudRepository<ReagentSlot, Integer> {
}
