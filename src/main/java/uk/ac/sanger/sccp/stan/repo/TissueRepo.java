package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Tissue;

public interface TissueRepo extends CrudRepository<Tissue, Integer> {
}
