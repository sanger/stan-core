package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.WorkType;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
public interface WorkTypeRepo extends CrudRepository<WorkType, Integer> {
    Optional<WorkType> findByName(String name);

    default WorkType getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Unknown work type: "+repr(name)));
    }

    List<WorkType> findAllByEnabled(boolean enabled);
}
