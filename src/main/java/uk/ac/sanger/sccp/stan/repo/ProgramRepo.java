package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Program;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Repo for Programs
 */
public interface ProgramRepo extends CrudRepository<Program, Integer> {
    Optional<Program> findByName(String name);

    default Program getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Program not found: "+repr(name)));
    }

    List<Program> findAllByEnabled(boolean enabled);

    List<Program> findAllByNameIn(Collection<String> names);

    /**
     * Gets the named programs. Throws an exception if any are not found.
     * @param names the names of programs
     * @return a list of matching programs, in no particular order
     * @exception EntityNotFoundException any of the named programs was not found
     */
    default List<Program> getAllByNameIn(Collection<String> names) throws EntityNotFoundException {
        List<Program> found = findAllByNameIn(names);
        if (found.size()==names.size()) {
            return found;
        }
        if (found.isEmpty()) {
            throw new EntityNotFoundException("Unknown programs: "+names);
        }
        Set<String> foundNamesUc = found.stream().map(p -> p.getName().toUpperCase()).collect(toSet());
        List<String> missing = names.stream().filter(name -> !foundNamesUc.contains(name.toUpperCase())).collect(toList());
        if (!missing.isEmpty()) {
            throw new EntityNotFoundException("Unknown programs: "+missing);
        }
        return found;
    }
}
