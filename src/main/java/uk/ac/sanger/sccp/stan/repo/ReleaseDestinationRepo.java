package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.ReleaseDestination;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface ReleaseDestinationRepo extends CrudRepository<ReleaseDestination, Integer> {
    List<ReleaseDestination> findAllByEnabled(boolean enabled);
    Optional<ReleaseDestination> findByName(String name);
    default ReleaseDestination getByName(String name) {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("No release destination found with name "+repr(name)));
    }
}
