package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.TreatmentType;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static java.util.stream.Collectors.toCollection;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface TreatmentTypeRepo extends CrudRepository<TreatmentType, Integer> {
    Optional<TreatmentType> findByName(String name);

    default TreatmentType getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Treatment type not found: " + repr(name)));
    }

    List<TreatmentType> findAllByEnabled(boolean enabled);

    List<TreatmentType> findByNameIn(Collection<String> names);

    default Set<TreatmentType> getSetByNameIn(Collection<String> names) {
        List<TreatmentType> types = findByNameIn(names);
        if (types.size() < names.size()) {
            UCMap<TreatmentType> ttMap = UCMap.from(types, TreatmentType::getName);
            Set<String> missing = names.stream()
                    .filter(name -> ttMap.get(name)==null)
                    .map(BasicUtils::repr)
                    .collect(toCollection(LinkedHashSet::new));
            if (!missing.isEmpty()) {
                throw new EntityNotFoundException("Unknown treatment type: " + missing);
            }
        }
        return new HashSet<>(types);
    }
}
