package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.WorkChangeLink;

public interface WorkChangeLinkRepo extends CrudRepository<WorkChangeLink, Integer> {
}
