package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Snapshot;

import java.util.Collection;
import java.util.List;

public interface SnapshotRepo extends CrudRepository<Snapshot, Integer> {
    List<Snapshot> findAllByIdIn(Collection<Integer> ids);
}
