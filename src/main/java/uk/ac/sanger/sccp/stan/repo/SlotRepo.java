package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Slot;

public interface SlotRepo extends CrudRepository<Slot, Integer> {
}
