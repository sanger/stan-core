package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabwarePrint;

/**
 * @author dr6
 */
public interface LabwarePrintRepo extends CrudRepository<LabwarePrint, Integer> {
}
