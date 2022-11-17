package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Release;

import javax.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.List;

/**
 * @author dr6
 */
public interface ReleaseRepo extends CrudRepository<Release, Integer> {
    List<Release> findAllByIdIn(Collection<Integer> ids);

    List<Release> findAllByLabwareIdIn(Collection<Integer> labwareIds);

    /**
     * Gets the releases matching the corresponding ids.
     * @param ids the ids to find
     * @return the releases in the order of the corresponding ids
     * @exception EntityNotFoundException any ids were not found
     */
    default List<Release> getAllByIdIn(Collection<Integer> ids) throws EntityNotFoundException {
        return RepoUtils.getAllByField(this::findAllByIdIn, ids, Release::getId,
                "Unknown release ID{s}: ", null);
    }
}
