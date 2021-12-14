package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.WorkType;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
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

    default List<WorkType> getAllByNameIn(Collection<String> names) {
        List<WorkType> found = findAllByNameIn(names);
        if (found.size() == names.size()) {
            return found;
        }
        if (found.isEmpty()) {
            throw new EntityNotFoundException("Unknown work types: "+names);
        }
        Set<String> foundNamesUc = found.stream().map(wt -> wt.getName().toUpperCase()).collect(toSet());
        List<String> missing = names.stream().filter(name -> !foundNamesUc.contains(name)).collect(toList());
        if (!missing.isEmpty()) {
            throw new EntityNotFoundException("Unknown work types: "+missing);
        }
        return found;
    }
}
