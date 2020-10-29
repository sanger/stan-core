package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Donor;

public interface DonorRepo extends CrudRepository<Donor, Integer> {
}
