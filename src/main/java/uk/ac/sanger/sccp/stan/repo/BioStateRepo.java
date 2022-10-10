package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.BioState;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface BioStateRepo extends CrudRepository<BioState, Integer> {
    Optional<BioState> findByName(String name);

    List<BioState> findAllByNameIn(Collection<String> names);

    default BioState getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("No bio state found with name "+repr(name)));
    }
}
