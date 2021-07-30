package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Project;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface ProjectRepo extends CrudRepository<Project, Integer> {
    Optional<Project> findByName(String name);

    default Project getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Project not found: "+repr(name)));
    }

    Iterable<Project> findAllByEnabled(boolean enabled);
}
