package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Medium;

import java.util.Optional;

public interface MediumRepo extends CrudRepository<Medium, Integer> {
    Optional<Medium> findByName(String name);
}
