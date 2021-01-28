package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.ReleaseDetail;

public interface ReleaseDetailRepo extends CrudRepository<ReleaseDetail, Integer> {
}
