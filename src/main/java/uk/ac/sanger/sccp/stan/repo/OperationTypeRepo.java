package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.OperationType;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

public interface OperationTypeRepo extends CrudRepository<OperationType, Integer> {
    Optional<OperationType> findByName(String name);

    default OperationType getByName(String name) {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Operation type not found: "+name));
    }
}
