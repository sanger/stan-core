package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabwareType;

public interface LabwareTypeRepo extends CrudRepository<LabwareType, Integer> {
}
