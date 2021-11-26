package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OpWithSlotMeasurementsRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

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
}
