package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.SlotRegion;

import java.util.Optional;

/**
 * Repo for {@link SlotRegion}
 * @author dr6
 */
public interface SlotRegionRepo extends CrudRepository<SlotRegion, Integer> {
    Iterable<SlotRegion> findAllByEnabled(boolean enabled);

    Optional<SlotRegion> findByName(String name);
}
