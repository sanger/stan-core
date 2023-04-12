package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.ReleaseRecipient;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface ReleaseRecipientRepo extends CrudRepository<ReleaseRecipient, Integer> {
    List<ReleaseRecipient> findAllByEnabled(boolean enabled);
    Optional<ReleaseRecipient> findByUsername(String username);
    default ReleaseRecipient getByUsername(String username) throws EntityNotFoundException {
        return findByUsername(username).orElseThrow(() -> new EntityNotFoundException("No release recipient found with username "+repr(username)));
    }

    List<ReleaseRecipient> findAllByUsernameIn(Collection<String> names);

    default List<ReleaseRecipient> getAllByUsernameIn(Collection<String> names) throws EntityNotFoundException {
        return RepoUtils.getAllByField(this::findAllByUsernameIn, names, ReleaseRecipient::getUsername,
                "Unknown recipient{s}: ", String::toLowerCase);
    }
}
