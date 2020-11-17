package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.PlanSample;

public interface PlanSampleRepo extends CrudRepository<PlanSample, Integer> {
}
