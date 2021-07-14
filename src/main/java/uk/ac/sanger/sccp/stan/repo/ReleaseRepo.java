package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Release;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static java.util.stream.Collectors.toMap;

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
        Map<Integer, Release> releaseIdMap = findAllByIdIn(ids).stream()
                .collect(toMap(Release::getId, r -> r));

        LinkedHashSet<Integer> missing = new LinkedHashSet<>();
        List<Release> releases = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            Release release = releaseIdMap.get(id);
            if (release==null) {
                missing.add(id);
            } else {
                releases.add(release);
            }
        }
        if (!missing.isEmpty()) {
            throw new EntityNotFoundException(String.format("Unknown release %s: %s",
                    missing.size()==1 ? "id" : "ids", missing));
        }
        return releases;
    }
}
