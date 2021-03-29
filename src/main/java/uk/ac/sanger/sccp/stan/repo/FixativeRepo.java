package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Fixative;

import java.util.*;

public interface FixativeRepo extends CrudRepository<Fixative, Integer> {
    Optional<Fixative> findByName(String name);

    List<Fixative> findAllByNameIn(Collection<String> names);
}
