package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LoginResult;

public interface LoginResultRepo extends CrudRepository<LoginResult, Integer> {
}
