package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.ReleaseRecipient;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface ReleaseRecipientRepo extends CrudRepository<ReleaseRecipient, Integer> {
    List<ReleaseRecipient> findAllByEnabled(boolean enabled);
    Optional<ReleaseRecipient> findByUsername(String username);
    default ReleaseRecipient getByUsername(String username) throws EntityNotFoundException {
        return findByUsername(username).orElseThrow(() -> new EntityNotFoundException("No release recipient found with username "+repr(username)));
    }
}
