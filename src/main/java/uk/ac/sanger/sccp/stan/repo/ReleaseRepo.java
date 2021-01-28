package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Release;

/**
 * @author dr6
 */
public interface ReleaseRepo extends CrudRepository<Release, Integer> {
}
