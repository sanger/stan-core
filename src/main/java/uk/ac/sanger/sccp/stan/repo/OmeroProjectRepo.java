package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.OmeroProject;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface OmeroProjectRepo extends CrudRepository<OmeroProject, Integer> {

    List<OmeroProject> findAllByEnabled(boolean enabled);

    Optional<OmeroProject> findByName(String name);

    default OmeroProject getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Omero project not found: "+repr(name)));
    }
}
