package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Program;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

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
}
