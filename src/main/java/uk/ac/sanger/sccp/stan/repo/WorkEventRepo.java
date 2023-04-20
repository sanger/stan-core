package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.model.WorkEvent;

import java.util.Collection;
import java.util.List;

/**
 * Repo for {@link WorkEvent work events}
 * @author dr6
 */
public interface WorkEventRepo extends CrudRepository<WorkEvent, Integer> {
    /**
     * Gets the latest work event (if any) for each of the given work ids.
     * In theory that means one event for each id specified.
     * This assumes that two work events on the same work id will not have the same timestamp,
     * otherwise both may be returned.
     * @param workIds work ids
     * @return the latest work event for each given work id
     */
    default Iterable<WorkEvent> getLatestEventForEachWorkId(Collection<Integer> workIds) {
        List<Integer> eventIds = _latestEventIdsForWorkIds(workIds);
        return findAllById(eventIds);
    }

    /**
     * Gets the id of the latest event for each of the given work ids.
     * @param workIds work ids
     * @return the latest event id (if any) for each of the given work ids
     */
    @Query(value="select e.id from work_event e join " +
            " (select max(e.performed) AS performed, e.work_id from work_event e " +
            " where e.work_id IN (?1) group by e.work_id) " +
            " as latest using (performed, work_id)",
            nativeQuery=true)
    List<Integer> _latestEventIdsForWorkIds(Collection<Integer> workIds);

    List<WorkEvent> findAllByWorkInAndType(Collection<Work> workIds, WorkEvent.Type type);
}
