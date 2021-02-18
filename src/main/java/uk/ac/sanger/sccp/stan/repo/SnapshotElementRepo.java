package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.SnapshotElement;

public interface SnapshotElementRepo extends CrudRepository<SnapshotElement, Integer> {
}
