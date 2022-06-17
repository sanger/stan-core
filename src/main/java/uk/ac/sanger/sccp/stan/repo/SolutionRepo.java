package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Solution;

import java.util.List;
import java.util.Optional;

public interface SolutionRepo extends CrudRepository<Solution, Integer> {
    Optional<Solution> findByName(String name);

    List<Solution> findAllByEnabled(boolean enabled);
}
