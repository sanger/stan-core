package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public interface WorkRepo extends CrudRepository<Work, Integer> {
    Optional<Work> findByWorkNumber(String workNumber);

    default Work getByWorkNumber(String workNumber) throws EntityNotFoundException {
        return findByWorkNumber(workNumber).orElseThrow(() -> new EntityNotFoundException("Unknown work number: "+workNumber));
    }

    @Query(value = "select prefix from work_sequence", nativeQuery = true)
    List<String> getPrefixes();

    /**
     * This increments the count for a given prefix.
     * This should be called inside a transaction before {@link #getCount}. This method will lock the row for the
     * transaction so another thread cannot produce the same result.
     * Call {@link #createNumber} instead of calling this directly.
     * @param prefix the work prefix to increment
     */
    @Modifying
    @Query(value = "update work_sequence set counter = (counter + 1) where prefix = ?1", nativeQuery = true)
    void _incrementCount(String prefix);

    /**
     * This gets the count for a given prefix.
     * If you're updating the counter, this should be called in a transaction and {@link #_incrementCount} should
     * be called first.
     * @param prefix the work prefix to get the count for
     * @return the count for the given prefix
     */
    @Query(value = "select counter from work_sequence where prefix = ?1", nativeQuery = true)
    int getCount(String prefix);

    /**
     * Increments the count for a prefix and returns the work number combining the prefix with the incremented number.
     * This should be called in a transaction.
     * @param prefix the prefix for the new work
     * @return a new work number using the given prefix and the updated count for that prefix
     */
    default String createNumber(String prefix) {
        _incrementCount(prefix);
        int n = getCount(prefix);
        return prefix.toUpperCase() + n;
    }

    /**
     * Gets work numbers in any of the given statuses
     * @param statuses a collection of statuses
     * @return the matching works
     */
    Iterable<Work> findAllByStatusIn(Collection<Status> statuses);

    List<Work> findAllByWorkTypeIn(Collection<WorkType> workTypes);


    @Query(value="select operation_id as opId, work_number as workNumber from work_op join work on (work_id=work.id) where operation_id IN (?1)", nativeQuery=true)
    List<Object[]> _opIdWorkNumbersForOpIds(Collection<Integer> opIds);

    default Map<Integer, Set<String>> findWorkNumbersForOpIds(Collection<Integer> opIds) {
        if (opIds.isEmpty()) {
            return Map.of();
        }
        final Map<Integer, Set<String>> opWork = opIds.stream().collect(toMap(Function.identity(), x -> new HashSet<>()));
        for (Object[] opIdWorkNumber : _opIdWorkNumbersForOpIds(opIds)) {
            opWork.get((Integer) opIdWorkNumber[0]).add((String) opIdWorkNumber[1]);
        }
        return opWork;
    }

    @Query(value="select release_id as releaseId, work_number as workNumber from work_release join work on (work_id=work.id) where release_id IN (?1)", nativeQuery=true)
    List<Object[]> _releaseIdWorkNumbersForReleaseIds(Collection<Integer> releaseIds);

    default Map<Integer, String> findWorkNumbersForReleaseIds(Collection<Integer> releaseIds) {
        if (releaseIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> map = new HashMap<>();
        for (Object[] ridWorkNum: _releaseIdWorkNumbersForReleaseIds(releaseIds)) {
            map.put((Integer) ridWorkNum[0], (String) ridWorkNum[1]);
        }
        return map;
    }

    List<Work> findAllByWorkNumberIn(Collection<String> workNumbers);

    List<Work> findAllByProgramIn(Collection<Program> programs);

    @Query(value="select distinct labware_id from work_sample ws join slot on (ws.slot_id=slot.id) where ws.work_id IN (?1)", nativeQuery = true)
    List<Integer> findLabwareIdsForWorkIds(Collection<Integer> workIds);

    /**
     * Gets set of work numbers based on sample and slot id
     * @param sampleId id of sample to search
     * @param slotId id of slot to search
     * @return set of the matching works
     */
    @Query(value="select * from work_sample ws join work on (ws.work_id=work.id) where ws.sample_id=(?1) and ws.slot_id = (?2)", nativeQuery = true)
    Set<Work> findWorkForSampleIdAndSlotId(Integer sampleId, Integer slotId);
}
