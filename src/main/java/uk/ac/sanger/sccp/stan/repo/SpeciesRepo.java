package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Species;

import java.util.Optional;

public interface SpeciesRepo extends CrudRepository<Species, Integer> {
    Optional<Species> findByName(String name);
}
