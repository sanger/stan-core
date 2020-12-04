package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabelType;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

public interface LabelTypeRepo extends CrudRepository<LabelType, Integer> {
    Optional<LabelType> findByName(String name);

    default LabelType getByName(String name) throws EntityNotFoundException {
        return findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Unknown label type: "+name));
    }
}
