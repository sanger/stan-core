package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;
import static uk.ac.sanger.sccp.utils.BasicUtils.stream;

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

    @Query(value = "SELECT MAX(work.id)" +
            " FROM (" +
            "   SELECT op.id" +
            "   FROM operation op" +
            "     JOIN action a ON (a.operation_id=op.id)" +
            "     JOIN slot ON (a.dest_slot_id=slot.id)" +
            "     JOIN work_op wo ON (wo.operation_id=op.id)" +
            "   WHERE slot.labware_id=?1" +
            "   ORDER BY op.performed DESC, op.id DESC" +
            "   LIMIT 1" +
            " ) AS latest_op" +
            "   JOIN work_op wo ON (wo.operation_id=latest_op.id)" +
            "   JOIN work ON (wo.work_id=work.id)" +
            " WHERE work.status='active'", nativeQuery = true)
    Integer findLatestActiveWorkIdForLabwareId(Integer labwareId);

    @Query(value = "SELECT MAX(work.id)" +
            " FROM (" +
            "   SELECT op.id" +
            "   FROM operation op" +
            "     JOIN action a ON (a.operation_id=op.id)" +
            "     JOIN slot ON (a.dest_slot_id=slot.id)" +
            "     JOIN work_op wo ON (wo.operation_id=op.id)" +
            "   WHERE slot.labware_id=?1" +
            "   ORDER BY op.performed DESC, op.id DESC" +
            "   LIMIT 1" +
            " ) AS latest_op" +
            "   JOIN work_op wo ON (wo.operation_id=latest_op.id)" +
            "   JOIN work ON (wo.work_id=work.id)", nativeQuery = true)
    Integer findLatestWorkIdForLabwareId(Integer labwareId);

    @Query(value="SELECT DISTINCT ws.work_id " +
            "FROM slot " +
            "JOIN work_sample ws ON (ws.slot_id=slot.id) " +
            "WHERE slot.labware_id=?1", nativeQuery = true)
    List<Integer> findWorkIdsForLabwareId(Integer labwareId);

    /**
     * Gets set of work numbers based on sample and slot id
     * @param sampleId id of sample to search
     * @param slotId id of slot to search
     * @return set of the matching works
     */
    @Query(value="select * from work_sample ws join work on (ws.work_id=work.id) where ws.sample_id=(?1) and ws.slot_id = (?2)", nativeQuery = true)
    Set<Work> findWorkForSampleIdAndSlotId(Integer sampleId, Integer slotId);

    @Query(value="select slot_id, sample_id, work_id from work_sample ws where ws.slot_id in (?1)", nativeQuery = true)
    List<Object[]> slotSampleWorkIdsForSlotIds(Collection<Integer> slotIds);

    /**
     * Loads works linked to the given slot ids.
     * @param slotIds slot ids to look for works
     * @return a map from slot/sample ids to the set of linked works
     */
    default Map<SlotIdSampleId, Set<Work>> slotSampleWorksForSlotIds(Collection<Integer> slotIds) {
        List<Object[]> rows = slotIds.isEmpty() ? List.of() : slotSampleWorkIdsForSlotIds(slotIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Set<Integer> workIds = rows.stream()
                .map(arr -> (Integer) arr[2])
                .collect(toSet());
        Map<Integer, Work> workMap = stream(findAllById(workIds))
                .collect(inMap(Work::getId));
        Map<SlotIdSampleId, Set<Work>> map = new HashMap<>();
        for (Object[] row: rows) {
            SlotIdSampleId key = new SlotIdSampleId((Integer) row[0], (Integer) row[1]);
            map.computeIfAbsent(key, k -> new HashSet<>()).add(workMap.get((Integer) row[2]));
        }
        return map;
    }

    default Set<Work> getSetByWorkNumberIn(Collection<String> workNumbers) throws EntityNotFoundException {
        return RepoUtils.getSetByField(this::findAllByWorkNumberIn, workNumbers, Work::getWorkNumber,
                "Unknown work number{s}: ", String::toUpperCase);
    }

    List<Work> findAllByWorkRequesterIn(Collection<ReleaseRecipient> requesters);

    default UCMap<Work> getMapByWorkNumberIn(Collection<String> workNumbers) throws EntityNotFoundException {
        return RepoUtils.getUCMapByField(this::findAllByWorkNumberIn, workNumbers, Work::getWorkNumber,
                "Missing work number{s} in database: ");
    }
}
