package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Release;
import uk.ac.sanger.sccp.stan.model.SlotIdSampleId;

import javax.persistence.EntityNotFoundException;
import java.util.*;

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

    @Query(value = "select r.id, se.slot_id, se.sample_id " +
            "from labware_release r " +
            "join snapshot_element se on (r.snapshot_id=se.snapshot_id) " +
            "where r.id in (?1)", nativeQuery = true)
    int[][] _loadReleaseSlotSampleIds(Collection<Integer> releaseIds);

    /**
     * Gets the slot and sample ids for each each specified release
     * @param releaseIds release ids to look up
     * @return a map of release id to the associated slot and sample ids
     */
    default Map<Integer, Set<SlotIdSampleId>> findReleaseSlotSampleIds(Collection<Integer> releaseIds) {
        if (releaseIds.isEmpty()) {
            return Map.of();
        }
        int[][] rssis = _loadReleaseSlotSampleIds(releaseIds);
        if (rssis==null || rssis.length==0) {
            return Map.of();
        }
        Map<Integer, Set<SlotIdSampleId>> releaseSlotSampleIds = new HashMap<>();
        for (int[] rssi : rssis) {
            releaseSlotSampleIds.computeIfAbsent(rssi[0], k -> new HashSet<>())
                    .add(new SlotIdSampleId(rssi[1], rssi[2]));
        }
        return releaseSlotSampleIds;
    }
}
