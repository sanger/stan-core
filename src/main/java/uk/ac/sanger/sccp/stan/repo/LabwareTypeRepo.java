package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabwareType;

import java.util.Optional;

public interface LabwareTypeRepo extends CrudRepository<LabwareType, Integer> {
    Optional<LabwareType> findByName(String name);
}
