package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.User;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface UserRepo extends CrudRepository<User, Integer> {
    Optional<User> findByUsername(String username);

    default User getByUsername(String username) throws EntityNotFoundException {
        return findByUsername(username).orElseThrow(() -> new EntityNotFoundException("User not found: "+repr(username)));
    }
}
