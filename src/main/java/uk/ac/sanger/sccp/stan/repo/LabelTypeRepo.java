package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabelType;

public interface LabelTypeRepo extends CrudRepository<LabelType, Integer> {
}
