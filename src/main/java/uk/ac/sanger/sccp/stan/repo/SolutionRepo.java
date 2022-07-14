package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Solution;

import java.util.*;

public interface SolutionRepo extends CrudRepository<Solution, Integer> {
    Optional<Solution> findByName(String name);

    List<Solution> findAllByEnabled(boolean enabled);

    List<Solution> findAllByNameIn(Collection<String> solutionNames);
}
