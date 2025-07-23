package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.CellClass;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface CellClassRepo extends CrudRepository<CellClass, Integer> {
    Optional<CellClass> findByName(String name);
    List<CellClass> findAllByNameIn(Collection<String> names);
    List<CellClass> findAllByEnabled(boolean enabled);

    default CellClass getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Unknown cell class: "+repr(name)));
    }

    default UCMap<CellClass> findMapByNameIn(Collection<String> names) {
        return UCMap.from(findAllByNameIn(names), CellClass::getName);
    }
}
