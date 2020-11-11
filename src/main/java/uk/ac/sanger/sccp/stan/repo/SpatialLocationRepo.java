package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.SpatialLocation;

public interface SpatialLocationRepo extends CrudRepository<SpatialLocation, Integer> {
}
