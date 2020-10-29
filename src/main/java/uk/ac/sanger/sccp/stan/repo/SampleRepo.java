package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Sample;

public interface SampleRepo extends CrudRepository<Sample, Integer> {
}
