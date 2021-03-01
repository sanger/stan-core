package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Measurement;

import java.util.Collection;
import java.util.List;

/**
 * @author dr6
 */
public interface MeasurementRepo extends CrudRepository<Measurement, Integer> {
    List<Measurement> findAllBySlotIdIn(Collection<Integer> slotIds);
}
