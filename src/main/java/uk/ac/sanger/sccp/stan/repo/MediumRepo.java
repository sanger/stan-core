package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Medium;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface MediumRepo extends CrudRepository<Medium, Integer> {
    Optional<Medium> findByName(String name);

    List<Medium> findAllByNameIn(Collection<String> names);

    default Medium getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("No medium found with name "+repr(name)));
    }
}
