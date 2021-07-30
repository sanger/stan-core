package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.SasEvent;

/**
 * Repo for {@link SasEvent SAS events}
 * @author dr6
 */
public interface SasEventRepo extends CrudRepository<SasEvent, Integer> {
}
