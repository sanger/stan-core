package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Labware;

public interface LabwareRepo extends CrudRepository<Labware, Integer> {
}
