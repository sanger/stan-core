package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Sample;
import uk.ac.sanger.sccp.stan.model.Slot;

import java.util.Collection;
import java.util.List;

public interface SlotRepo extends CrudRepository<Slot, Integer> {
    // There's some kind of warning about the argument here having the wrong type,
    // but it works fine.
    @SuppressWarnings("SpringDataRepositoryMethodParametersInspection")
    List<Slot> findDistinctBySamplesIn(Iterable<Sample> samples);

    List<Slot> findAllByIdIn(Collection<Integer> ids);

    @Query("select id from Slot where labwareId in (?1)")
    List<Integer> findSlotIdsByLabwareIdIn(Collection<Integer> labwareIds);
}
