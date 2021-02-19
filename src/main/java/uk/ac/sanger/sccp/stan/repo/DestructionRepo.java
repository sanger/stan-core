package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Destruction;

import java.util.Collection;
import java.util.List;

public interface DestructionRepo extends CrudRepository<Destruction, Integer> {
    List<Destruction> findAllByLabwareIdIn(Collection<Integer> labwareIds);
}
