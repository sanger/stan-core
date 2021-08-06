package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.SasType;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
public interface SasTypeRepo extends CrudRepository<SasType, Integer> {
    Optional<SasType> findByName(String name);

    default SasType getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Unknown SAS type: "+repr(name)));
    }

    List<SasType> findAllByEnabled(boolean enabled);
}
