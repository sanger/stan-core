package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.MouldSize;

import java.util.Optional;

public interface MouldSizeRepo extends CrudRepository<MouldSize, Integer> {
    Optional<MouldSize> findByName(String name);
}
