package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.TissueType;

import java.util.Optional;

public interface TissueTypeRepo extends CrudRepository<TissueType, Integer> {
    Optional<TissueType> findByName(String name);
}
