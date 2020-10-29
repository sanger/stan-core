package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.TissueType;

public interface TissueTypeRepo extends CrudRepository<TissueType, Integer> {
}
