package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Hmdmc;

import java.util.Optional;

public interface HmdmcRepo extends CrudRepository<Hmdmc, Integer> {
    Optional<Hmdmc> findByHmdmc(String hmdmc);
}
