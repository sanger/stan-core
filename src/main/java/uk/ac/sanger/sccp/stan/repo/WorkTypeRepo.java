package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.WorkType;

import javax.persistence.EntityNotFoundException;
import java.util.*;

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

    List<WorkType> findAllByNameIn(Collection<String> names);

    default List<WorkType> getAllByNameIn(Collection<String> names) throws EntityNotFoundException {
        return RepoUtils.getAllByField(this::findAllByNameIn, names, WorkType::getName,
                "Unknown work type{s}: ", String::toUpperCase);
    }
}
