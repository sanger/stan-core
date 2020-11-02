package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Donor;

import java.util.Optional;

public interface DonorRepo extends CrudRepository<Donor, Integer> {
    Optional<Donor> findByDonorName(String donorName);
}
