package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.TissueType;

import java.util.*;

public interface TissueTypeRepo extends CrudRepository<TissueType, Integer> {
    Optional<TissueType> findByName(String name);
    Optional<TissueType> findByCode(String code);

    List<TissueType> findAllByNameIn(Collection<String> names);
}
