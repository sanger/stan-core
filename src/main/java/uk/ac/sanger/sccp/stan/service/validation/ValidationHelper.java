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
     * Loads and checks the labware
     * @param barcodes barcodes of labware
     * @return the loaded labware, mapped from barcodes
     */
    UCMap<Labware> checkLabware(Collection<String> barcodes);

    /**
     * Loads and checks the work
     * @param workNumbers work numbers to load
     * @return the loaded work, mapped from work numbers
     */
    UCMap<Work> checkWork(Collection<String> workNumbers);

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
     * @param priorOpTime the time of the prior operation that must be before the given timestamp
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

    Equipment checkEquipment(Integer equipmentId, String category);

    Equipment checkEquipment(Integer equipmentId, String category, boolean required);

}
