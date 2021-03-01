package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabwareType;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

public interface LabwareTypeRepo extends CrudRepository<LabwareType, Integer> {
    Optional<LabwareType> findByName(String name);
    default LabwareType getByName(final String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Labware type not found: "+name));
    }
}
