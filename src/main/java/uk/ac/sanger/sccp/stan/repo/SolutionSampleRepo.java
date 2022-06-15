package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.SolutionSample;

import java.util.List;
import java.util.Optional;

public interface SolutionSampleRepo extends CrudRepository<SolutionSample, Integer> {
    Optional<SolutionSample> findByName(String name);

    List<SolutionSample> findAllByEnabled(boolean enabled);
}
