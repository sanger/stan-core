package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.SamplePosition;
import uk.ac.sanger.sccp.stan.model.SlotIdSampleId;

import java.util.Collection;
import java.util.List;

/**
 * Repo for {@link SamplePosition}
 */
public interface SamplePositionRepo extends CrudRepository<SamplePosition, SlotIdSampleId> {
    List<SamplePosition> findAllBySlotIdIn(Collection<Integer> slotIds);
}
