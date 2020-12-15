package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Measurement;

/**
 * @author dr6
 */
public interface MeasurementRepo extends CrudRepository<Measurement, Integer> {
}
