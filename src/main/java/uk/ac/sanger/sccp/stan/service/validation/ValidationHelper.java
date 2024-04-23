package uk.ac.sanger.sccp.stan.service.validation;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Helper for common operation validation tasks
 * @author dr6
 */
public interface ValidationHelper {
    /**
     * Gets the problems accumulated by this helper's validations
     * @return a collection of problems found
     */
    Set<String> getProblems();

    /**
     * Loads and checks the op type.
     * @param opName the name of the operation type to load
     * @param expectedFlags the flags the operation type is expected to have
     * @param expectedNotFlags the flags the operation type is expected not to have
     * @param opTypePredicate additional predicate to test if operation type can be used
     * @return the loaded op type, or null
     */
    OperationType checkOpType(String opName, Collection<OperationTypeFlag> expectedFlags,
                              Collection<OperationTypeFlag> expectedNotFlags, Predicate<OperationType> opTypePredicate);

    /**
     * Loads and checks the op type.
     * @param opName the name of the operation type to load
     * @param opTypePredicate predicate to test if operation type can be used
     * @return the loaded op type, or null
     */
    default OperationType checkOpType(String opName, Predicate<OperationType> opTypePredicate) {
        return checkOpType(opName, null, null, opTypePredicate);
    }

    /**
     * Loads and checks the op type.
     * @param opName the name of the operation type to load
     * @param expectedFlags the flags the operation type is expected to have
     * @return the loaded op type, or null
     */
    default OperationType checkOpType(String opName, OperationTypeFlag... expectedFlags) {
        return checkOpType(opName, Arrays.asList(expectedFlags), null, null);
    }

    /**
     * Loads and checks the labware as a source
     * @param barcodes barcodes of labware
     * @return the loaded labware, mapped from barcodes
     */
    UCMap<Labware> checkLabware(Collection<String> barcodes);

    /**
     * Loads labware and checks it is a valid active destination (for operations that support active destinations)
     * @param barcode the labware barcode
     * @return the loaded labware, if any
     */
    Labware loadActiveDestination(String barcode);

    /**
     * Loads labware and checks they are valid active destinations (for operations that support active destinations)
     * @param barcodes the labware barcodes
     * @return the loaded labware, if any
     */
    UCMap<Labware> loadActiveDestinations(Collection<String> barcodes);

    /**
     * Loads and checks the work
     * @param workNumbers work numbers to load
     * @return the loaded work, mapped from work numbers
     */
    UCMap<Work> checkWork(Collection<String> workNumbers);

    /**
     * Loads and checks a single work
     * @param workNumber work number to load
     * @return the single loaded work, if any
     */
    Work checkWork(String workNumber);

    /**
     * Loads and checks comments
     * @param commentIdStream stream of comment ids to check
     * @return loaded comments, mapped from their ids
     */
    Map<Integer, Comment> checkCommentIds(Stream<Integer> commentIdStream);

    /**
     * Checks the given timestamp seems appropriate
     * @param timestamp the timestamp to check (may be null, in which case nothing is checked)
     * @param today the date today
     * @param labware the labware that must be created before the given timestamp
     * @param priorOpTime the time of the prior operation that must be before the given timestamp (if appropriate)
     */
    void checkTimestamp(LocalDateTime timestamp, LocalDate today, Collection<Labware> labware, LocalDateTime priorOpTime);

    /**
     * Checks the given timestamp seems appropriate
     * @param timestamp the timestamp to check (may be null, in which case nothing is checked)
     * @param today the date today
     * @param lw the single labware (or null) that must be created before the given timestamp
     */
    default void checkTimestamp(LocalDateTime timestamp, LocalDate today, Labware lw) {
        List<Labware> labware = (lw==null ? null : List.of(lw));
        checkTimestamp(timestamp, today, labware, null);
    }

    /**
     * Checks the given timestamp seems appropriate
     * @param timestamp the timestamp to check (may be null, in which case nothing is checked)
     * @param today the date today
     * @param lw the single labware (or null) that must be created before the given timestamp
     * @param priorOpTime the time of the prior operation that must be before the given timestamp (if appropriate)
     */
    default void checkTimestamp(LocalDateTime timestamp, LocalDate today, Labware lw, LocalDateTime priorOpTime) {
        List<Labware> labware = (lw==null ? null : List.of(lw));
        checkTimestamp(timestamp, today, labware, priorOpTime);
    }

    /**
     * Loads and checks equipment. If the id is null, returns null.
     * @param equipmentId the id of the equipment
     * @param category the required category of the equipment
     * @return the loaded equipment, or null
     */
    default Equipment checkEquipment(Integer equipmentId, String category) {
        return checkEquipment(equipmentId, category, false);
    }

    /**
     * Loads and checks equipment. If the id is null, returns null. If {@code required} is true, and
     * the id is null, a problem will be added.
     * @param equipmentId the id of the equipment
     * @param category the required category of the equipment
     * @return the loaded equipment
     */
    Equipment checkEquipment(Integer equipmentId, String category, boolean required);

}
