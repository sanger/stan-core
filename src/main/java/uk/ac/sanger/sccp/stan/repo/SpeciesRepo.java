package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Species;

import java.util.*;

public interface SpeciesRepo extends CrudRepository<Species, Integer> {
    Optional<Species> findByName(String name);

    List<Species> findAllByNameIn(Collection<String> names);

    Iterable<Species> findAllByEnabled(boolean enabled);
}
