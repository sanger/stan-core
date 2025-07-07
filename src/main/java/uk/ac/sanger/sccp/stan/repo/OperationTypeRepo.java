package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.OperationType;

import javax.persistence.EntityNotFoundException;
import java.util.*;

public interface OperationTypeRepo extends CrudRepository<OperationType, Integer> {
    Optional<OperationType> findByName(String name);
    List<OperationType> findByNameIn(Collection<String> names);

    /**
     * Gets an operation type by name; throws an error if it is not found.
     * @param name the name of the operation type
     * @return the operation type found
     * @exception EntityNotFoundException no such entity was found
     */
    default OperationType getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Operation type not found: "+name));
    }
}
