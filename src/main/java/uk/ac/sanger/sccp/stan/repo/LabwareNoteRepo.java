package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabwareNote;

import java.util.Collection;
import java.util.List;

/**
 * Repo for saving and retrieving key/value string pairs
 * linked to a particular labware and operation.
 */
public interface LabwareNoteRepo extends CrudRepository<LabwareNote, Integer> {
    List<LabwareNote> findAllByOperationIdIn(Collection<Integer> opIds);
}
