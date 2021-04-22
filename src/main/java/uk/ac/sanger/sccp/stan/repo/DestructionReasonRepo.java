package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.DestructionReason;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

public interface DestructionReasonRepo extends CrudRepository<DestructionReason, Integer> {
    List<DestructionReason> findAllByEnabled(boolean enabled);

    default DestructionReason getById(Integer id) throws EntityNotFoundException {
        return findById(id).orElseThrow(() -> new EntityNotFoundException("No destruction reason found with id "+id));
    }

    Optional<DestructionReason> findByText(String text);
}
