package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Sample;
import uk.ac.sanger.sccp.stan.model.Slot;

import java.util.Collection;
import java.util.List;

public interface SlotRepo extends CrudRepository<Slot, Integer> {
    List<Slot> findDistinctBySamplesIn(Iterable<Sample> samples);

    List<Slot> findAllByIdIn(Collection<Integer> ids);
}
