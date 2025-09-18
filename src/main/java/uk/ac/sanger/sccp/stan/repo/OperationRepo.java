package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

public interface OperationRepo extends CrudRepository<Operation, Integer> {
    @Query("select distinct op from Operation op join Action a on (a.operationId=op.id) " +
            "where op.operationType=?1 and a.sample.id in (?2)")
    List<Operation> findAllByOperationTypeAndSampleIdIn(OperationType opType, Collection<Integer> sampleIds);

    @Query("select distinct op from Operation op join Action a ON (a.operationId=op.id) where a.sample.id IN (?1)")
    List<Operation> findAllBySampleIdIn(Collection<Integer> sampleIds);

    @Query("select distinct op from Operation op join Action a on (a.operationId=op.id) " +
            "join Slot s on (a.destination=s) " +
            "where op.operationType=?1 and s.labwareId in (?2)")
    List<Operation> findAllByOperationTypeAndDestinationLabwareIdIn(OperationType opType, Collection<Integer> labwareIds);

    @Query("select distinct op from Operation op join Action a on (a.operationId=op.id) " +
            "join Slot s on (a.destination=s) " +
            "where op.operationType in (?1) and s.labwareId in (?2)")
    List<Operation> findAllByOperationTypeInAndDestinationLabwareIdIn(Collection<OperationType> opTypes, Collection<Integer> labwareIds);

    @Query("select distinct op from Operation op join Action a on (a.operationId=op.id) " +
            "where op.operationType=?1 and a.destination.id in (?2)")
    List<Operation> findAllByOperationTypeAndDestinationSlotIdIn(OperationType opType, Collection<Integer> slotIds);

    @Query("select distinct op from Operation op join Action a on (a.operationId=op.id) " +
            "where op.operationType in (?1) and a.destination.id in (?2)")
    List<Operation> findAllByOperationTypeInAndDestinationSlotIdIn(Collection<OperationType> opTypes, Collection<Integer> slotIds);

    List<Operation> findAllByOperationType(OperationType opType);

    @Query(value = "select distinct a.operation_id, a.dest_slot_id, a.sample_id " +
            "from action a " +
            "where a.operation_id in (?1)", nativeQuery = true)
    int[][] _loadOpSlotSampleIds(Collection<Integer> opIds);

    @Query(value = "select slot.labware_id, MIN(op.performed) as performed " +
            "from slot join action a on (a.dest_slot_id=slot.id) " +
            "join operation op on (a.operation_id=op.id) " +
            "where slot.labware_id in (?1) " +
            "group by slot.labware_id",
            nativeQuery = true)
    Object[][] _loadLabwareFirstTimestamp(Collection<Integer> labwareIds);

    /**
     * Gets the slot and sample ids for each each specified operation
     * @param opIds operation ids to look up
     * @return a map of operation id to the associated slot and sample ids
     */
    default Map<Integer, Set<SlotIdSampleId>> findOpSlotSampleIds(Collection<Integer> opIds) {
        if (opIds.isEmpty()) {
            return Map.of();
        }
        int[][] ossis = _loadOpSlotSampleIds(opIds);
        if (ossis==null || ossis.length==0) {
            return Map.of();
        }
        Map<Integer, Set<SlotIdSampleId>> opSlotSampleIds = new HashMap<>();
        for (int[] ossi : ossis) {
            opSlotSampleIds.computeIfAbsent(ossi[0], k -> new HashSet<>())
                    .add(new SlotIdSampleId(ossi[1], ossi[2]));
        }
        return opSlotSampleIds;
    }

    /**
     * Finds the earlier timestamp of an operation into the specified labware.
     * This is useful as a creation timestamp for the labware.
     * @param labwareIds the labware ids to check
     * @return a map from labware id to timestamp
     */
    default Map<Integer, LocalDateTime> findEarliestPerformedIntoLabware(Collection<Integer> labwareIds) {
        if (labwareIds.isEmpty()) {
            return Map.of();
        }
        Object[][] data = _loadLabwareFirstTimestamp(labwareIds);
        Map<Integer, LocalDateTime> lwTime = new HashMap<>(data.length);
        for (Object[] row : data) {
            LocalDateTime time;
            if (row[1] instanceof Timestamp ts) {
                time = ts.toLocalDateTime();
            } else {
                time = (LocalDateTime) row[1];
            }
            lwTime.put((Integer) row[0], time);
        }
        return lwTime;
    }

}
