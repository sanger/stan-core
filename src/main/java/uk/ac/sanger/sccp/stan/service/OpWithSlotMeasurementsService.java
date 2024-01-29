package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.*;

import java.util.*;

/**
 * Service for performing an operation recording measurements in slots.
 * @author dr6
 */
public interface OpWithSlotMeasurementsService {
    /**
     * Validates and executes the given request
     * @param user the user responsible for the request
     * @param request the request specifying what to perform
     * @return the operations and labware
     * @exception ValidationException if the validation fails
     */
    OperationResult perform(User user, OpWithSlotMeasurementsRequest request) throws ValidationException;

    void validateAddresses(Collection<String> problems, LabwareType lt, Set<Address> filledSlotAddresses,
                           List<SlotMeasurementRequest> slotMeasurements);

    /**
     * Checks for problems with comment ids, if any.
     * @param problems receptacle for problems found
     * @param sms the slot measurement requests
     * @return the indicated comments
     */
    List<Comment> validateComments(Collection<String> problems, Collection<SlotMeasurementRequest> sms);

    /**
     * Validates and sanitises measurement names and values.
     * Problems include missing names, missing values, invalid names (for the op type), and whatever problems are
     * found when sanitising the measurement value.
     * @param problems receptacle for problems found
     * @param opType the op type requested
     * @param slotMeasurements the requested measurements
     * @return the validated measurements
     */
    List<SlotMeasurementRequest> sanitiseMeasurements(Collection<String> problems, OperationType opType,
                                                      Collection<SlotMeasurementRequest> slotMeasurements);

    /**
     * Checks for occurrences where the same measurement name is requested in the same address.
     * The measurement names should already be sanitised so that they are easy to identify.
     * @param problems receptacle for problems found
     * @param smrs the measurement requests
     */
    void checkForDupeMeasurements(Collection<String> problems, Collection<SlotMeasurementRequest> smrs);

    /**
     * Executes the request. Records the op; links it to the given work (if any); records the measurements (if any)
     * @param user the user responsible for the request
     * @param lw the labware to record the operation and measurements on
     * @param opType the type of op to record
     * @param work the work to link to the op (if any)
     * @param sanitisedMeasurements the specification of what measurements to record
     * @return the op and labware
     */
    OperationResult execute(User user, Labware lw, OperationType opType, Work work,
                            Collection<Comment> comments,
                            Collection<SlotMeasurementRequest> sanitisedMeasurements);

    /**
     * Loads the op type and checks it is suitable
     * @param problems receptacle for problems
     * @param opName name of the operation type
     * @return the loaded operation type, or null
     */
    OperationType loadOpType(Collection<String> problems, String opName);
}
